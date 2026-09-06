package com.apiweb.engine;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP 执行器。
 *
 * <p>基于 JDK 11+ 内置的 {@link HttpClient}（不引入 Apache HttpClient / OkHttp 等第三方库）。
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>支持 {{var}} 占位符自动替换（URL / Headers / Body 中的）</li>
 *   <li>Query 参数自动拼接到 URL 并做 URL 编码</li>
 *   <li>默认 60s 超时、10s 连接超时</li>
 *   <li>自动跟随普通重定向（{@code Redirect.NORMAL}，不跟随跨协议）</li>
 *   <li>当 Body 不为空且方法非 GET/HEAD/DELETE 时，自动补 Content-Type 为 application/json（用户已设置则不覆盖）</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * {@link HttpClient} 实例是线程安全的，可被多线程共用。本类作为 Spring 单例 Bean，无状态。
 */
@Component
public class HttpExecutor {

    /**
     * 全局共享的 HttpClient 实例。
     * <p>连接池、超时配置在创建时确定；所有 HTTP 请求复用同一客户端以提升性能。</p>
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 响应数据结构：状态码、Headers、Body、耗时。
     */
    public record Response(int status, Map<String, List<String>> headers, String body, long durationMs) {}

    /**
     * 执行单次 HTTP 请求。
     *
     * @param step     HTTP 步骤定义（已通过 {@link EngineDtos.HttpStep} 解析）
     * @param resolver 变量解析器（用于替换占位符）
     * @return 响应数据（含耗时）
     * @throws Exception 网络异常、URL 不合法、超时等都会抛
     */
    public Response execute(EngineDtos.HttpStep step, VariablesResolver resolver) throws Exception {
        // 1. URL 变量替换 + 协议补全
        String url = resolver.resolve(step.getUrl());
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("请求 URL 为空");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        // 2. Query 参数拼接（自动 URL 编码）
        StringBuilder urlWithQuery = new StringBuilder(url);
        if (step.getQuery() != null && !step.getQuery().isEmpty()) {
            String qs = step.getQuery().stream()
                    .filter(kv -> kv.getEnabled() == null || kv.getEnabled())
                    .map(kv -> kv.getKey() + "=" + java.net.URLEncoder.encode(
                            resolver.resolve(kv.getValue() == null ? "" : kv.getValue()),
                            StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            if (!qs.isEmpty()) {
                urlWithQuery.append(url.contains("?") ? "&" : "?").append(qs);
            }
        }

        // 3. Headers 拼装（启用过滤 + 变量替换）
        Map<String, String> headers = new LinkedHashMap<>();
        if (step.getHeaders() != null) {
            step.getHeaders().stream()
                    .filter(kv -> kv.getEnabled() == null || kv.getEnabled())
                    .forEach(kv -> headers.put(kv.getKey(), resolver.resolve(kv.getValue())));
        }

        // 4. 根据方法 + body 决定 HttpRequest 类型
        String method = step.getMethod() == null ? "GET" : step.getMethod().toUpperCase();
        String body = resolver.resolve(step.getBody());
        boolean hasBody = body != null && !body.isBlank()
                && !List.of("GET", "HEAD", "DELETE").contains(method);

        HttpRequest.Builder builder;
        if (hasBody) {
            builder = HttpRequest.newBuilder()
                    .uri(URI.create(urlWithQuery.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else if (!method.equals("GET")) {
            // 例如 DELETE / POST（空 body）/ PUT 等
            builder = HttpRequest.newBuilder()
                    .uri(URI.create(urlWithQuery.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder = HttpRequest.newBuilder()
                    .uri(URI.create(urlWithQuery.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .GET();
        }
        headers.forEach(builder::header);
        // 自动补 Content-Type（仅在用户没设置时）
        if (hasBody && !headers.containsKey("Content-Type")) {
            builder.header("Content-Type", "application/json");
        }

        // 5. 真正发起请求 + 计时
        long start = System.currentTimeMillis();
        HttpResponse<String> response =
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        long duration = System.currentTimeMillis() - start;
        return new Response(response.statusCode(), response.headers().map(),
                response.body(), duration);
    }

    /**
     * 把响应头 Map 的 key 全部转小写。
     *
     * <p>HTTP Header 名大小写不敏感，统一为小写便于断言 / 提取时比较。
     * 注意：如果原 Map 中存在同名不同大小写的多个 Header（如 "X-Token" 和 "x-token"），
     * 这里用 merge 函数保留第一个出现的值。</p>
     */
    public static Map<String, List<String>> lowercaseHeaders(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue,
                        (a, b) -> a));
    }
}
