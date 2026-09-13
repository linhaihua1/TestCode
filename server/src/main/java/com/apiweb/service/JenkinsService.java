package com.apiweb.service;

import com.apiweb.config.CiProperties;
import com.apiweb.entity.CiJenkinsServerEntity;
import com.apiweb.engine.JenkinsBuildResult;
import com.apiweb.security.AesGcm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jenkins Remote API 客户端：封装「连通性测试 / Job 列表 / 触发构建 / 查询构建 / 拉取日志」。
 *
 * <h3>为什么用 JDK HttpClient</h3>
 * Jenkins 调用是低频、需要精细控制超时与等待策略的外部调用，使用 JDK 17 自带的
 * {@link HttpClient} 可以避免引入额外依赖，且能按请求单独设置超时。
 *
 * <h3>调用链路</h3>
 * <ol>
 *   <li>取 Jenkins 的 CSRF crumb（{@code /crumbIssuer/api/json}）；未开启 CSRF 的 Jenkins 会 404，忽略即可</li>
 *   <li>POST {@code /job/{job}/buildWithParameters?k=v} 触发构建，成功返回 201，
 *       {@code Location} 头是队列项地址</li>
 *   <li>轮询队列项 {@code /queue/item/{n}/api/json} 解析出真正的构建号</li>
 *   <li>轮询构建 {@code /job/{job}/{no}/api/json} 直到 {@code building=false}</li>
 * </ol>
 *
 * <h3>安全</h3>
 * API Token 在库中以 AES-GCM 密文存储，仅在本服务内部调用时解密使用，接口出参不会返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JenkinsService {

    private final CiProperties ciProperties;
    private final ObjectMapper objectMapper;

    /** Jenkins 默认认证方式（用户名 + API Token 的 Basic 认证） */
    private static final String AUTH_BASIC = "basic";
    /** 仅使用 Token 的方式（Authorization: Bearer） */
    private static final String AUTH_TOKEN = "token";

    // ==================================================================
    // 对外能力
    // ==================================================================

    /**
     * 连通性测试：读取 Jenkins 根 API，验证地址与凭据是否可用。
     *
     * @param server Jenkins 服务器配置（含密文 Token）
     * @return 连通结果（含 Jenkins 版本号）
     */
    public ConnectionResult testConnection(CiJenkinsServerEntity server) {
        try {
            HttpResponse<String> resp = get(server, "/api/json", Map.of());
            if (resp.statusCode() >= 400) {
                return new ConnectionResult(false, null,
                        "HTTP " + resp.statusCode() + "：" + abbreviate(resp.body()));
            }
            JsonNode root = objectMapper.readTree(resp.body());
            String version = root.path("jenkinsVersion").asText(null);
            return new ConnectionResult(true, version, "连接成功");
        } catch (Exception e) {
            log.warn("Jenkins 连通性测试失败: name={}, url={}", server.getName(), server.getBaseUrl(), e);
            return new ConnectionResult(false, null, "连接失败：" + e.getMessage());
        }
    }

    /**
     * 拉取 Jenkins 顶层 Job 名称列表（含文件夹，文件夹内的 Job 需拼接 folder/job）。
     *
     * @param server Jenkins 服务器配置
     * @return Job 名称列表；失败返回空列表
     */
    public List<String> listJobs(CiJenkinsServerEntity server) {
        try {
            // tree=jobs[name,color] 只取需要的字段，减少大实例上的响应体积
            HttpResponse<String> resp = get(server, "/api/json", Map.of("tree", "jobs[name,color]"));
            if (resp.statusCode() >= 400) {
                log.warn("拉取 Jenkins Job 列表失败: HTTP {}", resp.statusCode());
                return List.of();
            }
            JsonNode jobs = objectMapper.readTree(resp.body()).path("jobs");
            List<String> names = new ArrayList<>();
            for (JsonNode job : jobs) {
                names.add(job.path("name").asText());
            }
            return names;
        } catch (Exception e) {
            log.warn("拉取 Jenkins Job 列表异常", e);
            return List.of();
        }
    }

    /**
     * 触发 Jenkins Job 构建（带参数）。
     *
     * @param server  Jenkins 服务器配置
     * @param jobName Job 名称（支持 {@code folder/job} 多级路径）
     * @param params  构建参数（可为 null / 空）
     * @return 触发结果：成功时携带队列项地址，供后续解析构建号
     */
    public TriggerResult trigger(CiJenkinsServerEntity server, String jobName, Map<String, String> params) {
        try {
            String path = jobPath(jobName) + "/buildWithParameters";
            // 参数拼在 query 上，Jenkins 同时接受 query 与表单，query 方式最通用
            Map<String, String> query = new LinkedHashMap<>();
            if (params != null) {
                params.forEach((k, v) -> {
                    if (k != null && v != null) query.put(k, v);
                });
            }
            HttpResponse<String> resp = post(server, path, query);
            if (resp.statusCode() >= 400) {
                return new TriggerResult(false, null,
                        "触发失败 HTTP " + resp.statusCode() + "：" + abbreviate(resp.body()));
            }
            String queueUrl = resp.headers().firstValue("Location").orElse(null);
            return new TriggerResult(true, queueUrl, "已触发构建");
        } catch (Exception e) {
            log.warn("触发 Jenkins 构建失败: job={}", jobName, e);
            return new TriggerResult(false, null, "触发失败：" + e.getMessage());
        }
    }

    /**
     * 轮询队列项地址解析构建号。
     * <p>Jenkins 触发后任务先进入队列，被调度执行后才生成构建号，因此需要等待。
     *
     * @param server   Jenkins 服务器配置
     * @param queueUrl 队列项地址（触发响应的 Location 头）
     * @return 构建号；超时或失败返回 null
     */
    public Integer resolveBuildNumber(CiJenkinsServerEntity server, String queueUrl) {
        if (queueUrl == null || queueUrl.isBlank()) return null;
        CiProperties.Jenkins cfg = ciProperties.getJenkins();
        long deadline = System.currentTimeMillis() + Math.max(1000, cfg.getQueuePollTimeoutMs());
        try {
            // 队列地址是完整 URL，需要转成相对路径以便统一走 get() 的鉴权与超时逻辑
            while (System.currentTimeMillis() < deadline) {
                JsonNode root = objectMapper.readTree(getAbsolute(server, queueUrl + "api/json").body());
                if (root.path("cancelled").asBoolean(false)) {
                    log.warn("Jenkins 队列任务被取消: {}", queueUrl);
                    return null;
                }
                JsonNode executable = root.get("executable");
                if (executable != null && !executable.isNull() && executable.has("number")) {
                    return executable.get("number").asInt();
                }
                Thread.sleep(Math.max(200, cfg.getQueuePollIntervalMs()));
            }
            log.warn("解析 Jenkins 构建号超时: {}", queueUrl);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("解析 Jenkins 构建号异常: {}", queueUrl, e);
        }
        return null;
    }

    /**
     * 查询构建状态。
     *
     * @param server  Jenkins 服务器配置
     * @param jobName Job 名称
     * @param buildNo 构建号
     * @return 构建信息（building / result / url）
     */
    public BuildInfo getBuild(CiJenkinsServerEntity server, String jobName, int buildNo) {
        try {
            String path = jobPath(jobName) + "/" + buildNo + "/api/json";
            HttpResponse<String> resp = get(server, path, Map.of());
            if (resp.statusCode() == 404) {
                return new BuildInfo(false, false, JenkinsBuildResult.NOT_FOUND, null);
            }
            if (resp.statusCode() >= 400) {
                return new BuildInfo(false, false, JenkinsBuildResult.UNKNOWN, null);
            }
            JsonNode root = objectMapper.readTree(resp.body());
            boolean building = root.path("building").asBoolean(false);
            // building=true 时 result 为 null → 解析为 RUNNING
            JenkinsBuildResult result = building
                    ? JenkinsBuildResult.RUNNING
                    : JenkinsBuildResult.parse(root.path("result").asText(null));
            return new BuildInfo(true, building, result, root.path("url").asText(null));
        } catch (Exception e) {
            log.warn("查询 Jenkins 构建状态失败: job={}, buildNo={}", jobName, buildNo, e);
            return new BuildInfo(false, false, JenkinsBuildResult.UNKNOWN, null);
        }
    }

    /**
     * 拉取构建控制台日志（用于流水线执行详情展示，超长会截断）。
     *
     * @param server  Jenkins 服务器配置
     * @param jobName Job 名称
     * @param buildNo 构建号
     * @return 控制台文本；失败返回 null
     */
    public String consoleText(CiJenkinsServerEntity server, String jobName, int buildNo) {
        try {
            String path = jobPath(jobName) + "/" + buildNo + "/consoleText";
            HttpResponse<String> resp = get(server, path, Map.of());
            if (resp.statusCode() >= 400) return null;
            int max = Math.max(1000, ciProperties.getJenkins().getConsoleMaxChars());
            String body = resp.body();
            // 只保留尾部内容：CI 日志里最后一段通常才是失败原因
            return body == null ? null : (body.length() <= max ? body : body.substring(body.length() - max));
        } catch (Exception e) {
            log.warn("拉取 Jenkins 控制台日志失败: job={}, buildNo={}", jobName, buildNo, e);
            return null;
        }
    }

    // ==================================================================
    // HTTP 基础方法
    // ==================================================================

    /**
     * 带鉴权的 GET 请求。
     *
     * @param server Jenkins 配置
     * @param path   以 / 开头的相对路径
     * @param query  query 参数（值会做 URL 编码）
     * @return 原始响应
     */
    private HttpResponse<String> get(CiJenkinsServerEntity server, String path, Map<String, String> query)
            throws Exception {
        return send(server, url(server, path, query), null);
    }

    /**
     * 带鉴权与 crumb 的 POST 请求（用于触发构建）。
     */
    private HttpResponse<String> post(CiJenkinsServerEntity server, String path, Map<String, String> query)
            throws Exception {
        return send(server, url(server, path, query), crumbHeader(server));
    }

    /**
     * 请求绝对地址（队列项地址等）。
     */
    private HttpResponse<String> getAbsolute(CiJenkinsServerEntity server, String absoluteUrl) throws Exception {
        return send(server, absoluteUrl, null);
    }

    /**
     * 统一发送：设置鉴权、CSRF crumb、超时。
     *
     * @param server     Jenkins 配置
     * @param url        完整 URL
     * @param crumbExtra 额外的 crumb 头（触发构建时需要），可为 null
     */
    private HttpResponse<String> send(CiJenkinsServerEntity server, String url, Map<String, String> crumbExtra)
            throws Exception {
        CiProperties.Jenkins cfg = ciProperties.getJenkins();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, cfg.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(500, cfg.getReadTimeoutMs())))
                .header("Accept", "application/json");
        authHeader(server).forEach(builder::header);
        if (crumbExtra != null) {
            crumbExtra.forEach(builder::header);
        }
        if (crumbExtra == null) {
            builder.GET();
        } else {
            // 触发构建：POST + 空 body（参数已在 URL 上）
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 构造鉴权头：basic（用户名:Token 的 Basic 认证）或 token（Bearer）。
     */
    private Map<String, String> authHeader(CiJenkinsServerEntity server) {
        String token = plainToken(server);
        if (AUTH_TOKEN.equalsIgnoreCase(server.getAuthType())) {
            return token == null ? Map.of() : Map.of("Authorization", "Bearer " + token);
        }
        String user = server.getUsername() == null ? "" : server.getUsername();
        String raw = user + ":" + (token == null ? "" : token);
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return Map.of("Authorization", "Basic " + encoded);
    }

    /**
     * 获取 Jenkins CSRF crumb 头；未开启 CSRF 保护时返回空 Map（不影响调用）。
     */
    private Map<String, String> crumbHeader(CiJenkinsServerEntity server) {
        try {
            HttpResponse<String> resp = get(server, "/crumbIssuer/api/json", Map.of());
            if (resp.statusCode() >= 400) return Map.of();
            JsonNode root = objectMapper.readTree(resp.body());
            String field = root.path("crumbRequestField").asText(null);
            String crumb = root.path("crumb").asText(null);
            if (field == null || crumb == null) return Map.of();
            return Map.of(field, crumb);
        } catch (Exception e) {
            log.debug("获取 Jenkins crumb 失败（可能未开启 CSRF 保护）: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解密 API Token：库中存的是 AES-GCM 密文；非密文（历史数据）原样返回。
     */
    private String plainToken(CiJenkinsServerEntity server) {
        String token = server.getApiToken();
        if (token == null || token.isBlank()) return null;
        try {
            return AesGcm.isCipherText(token) ? AesGcm.decrypt(token) : token;
        } catch (Exception e) {
            log.warn("Jenkins API Token 解密失败，按明文处理: server={}", server.getName());
            return token;
        }
    }

    /**
     * 拼接完整 URL：baseUrl + path + query（query 值做 URL 编码）。
     */
    private String url(CiJenkinsServerEntity server, String path, Map<String, String> query) {
        String base = trimSlash(server.getBaseUrl());
        StringBuilder sb = new StringBuilder(base).append(path);
        if (query != null && !query.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (!first) sb.append('&');
                first = false;
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    /**
     * 把 Job 名称（可能含 folder/subfolder/job）转成 Jenkins 的 {@code /job/a/job/b} 路径。
     */
    private String jobPath(String jobName) {
        StringBuilder sb = new StringBuilder();
        for (String seg : jobName.split("/")) {
            if (seg.isBlank()) continue;
            sb.append("/job/").append(URLEncoder.encode(seg, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String trimSlash(String s) {
        if (s == null) return "";
        String v = s.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private String abbreviate(String s) {
        if (s == null) return "";
        String v = s.replaceAll("\\s+", " ").trim();
        return v.length() <= 200 ? v : v.substring(0, 200) + "...";
    }

    // ==================================================================
    // 结果模型
    // ==================================================================

    /** 连通性测试结果 */
    @Data
    public static class ConnectionResult {
        private final boolean success;
        /** Jenkins 版本号（如 2.440.1） */
        private final String version;
        private final String message;

        public ConnectionResult(boolean success, String version, String message) {
            this.success = success;
            this.version = version;
            this.message = message;
        }
    }

    /** 触发构建结果 */
    @Data
    public static class TriggerResult {
        private final boolean success;
        /** 队列项地址（用于解析构建号） */
        private final String queueUrl;
        private final String message;

        public TriggerResult(boolean success, String queueUrl, String message) {
            this.success = success;
            this.queueUrl = queueUrl;
            this.message = message;
        }
    }

    /** 构建状态 */
    @Data
    public static class BuildInfo {
        private final boolean exists;
        private final boolean building;
        private final JenkinsBuildResult result;
        private final String url;

        public BuildInfo(boolean exists, boolean building, JenkinsBuildResult result, String url) {
            this.exists = exists;
            this.building = building;
            this.result = result;
            this.url = url;
        }
    }
}
