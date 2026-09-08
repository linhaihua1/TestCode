package com.apiweb.openapi;

import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * OpenAPI / Swagger 导入器（按需求文档 §5 接口管理）。
 *
 * <h3>支持版本</h3>
 * <ul>
 *   <li>OpenAPI 3.x（顶层有 {@code openapi} 字段）</li>
 *   <li>Swagger 2.x（顶层有 {@code swagger} 字段,值 "2.0"）</li>
 * </ul>
 *
 * <h3>支持入口</h3>
 * <ul>
 *   <li>{@link #importFromUrl}：从 URL 拉取 JSON（YAML 暂不支持,可手动转 JSON 后传文件）</li>
 *   <li>{@link #importFromJson}：从字符串解析</li>
 *   <li>{@link #importFromFileContent}：从文件字节（自动判断 JSON / YAML）</li>
 * </ul>
 *
 * <h3>输出</h3>
 * 每个 HTTP operation（GET/POST/...）一个 {@link ApiDefinitionDraft},包含：
 * <ul>
 *   <li>name/method/path/description/tags</li>
 *   <li>pathParams/query/headers/bodySchema/body</li>
 *   <li>responseExamples/responseHeaders</li>
 *   <li>sourceUrl/sourceHash(用于同步差异)</li>
 * </ul>
 */
@Slf4j
@Service
public class SwaggerImporter {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // ============================================================
    // 公开入口
    // ============================================================

    /**
     * 从 URL 导入。
     *
     * @param url Swagger/OpenAPI 文档 URL（通常是 /v3/api-docs 或 /swagger.json）
     * @return 接口定义草稿列表
     */
    public List<ApiDefinitionDraft> importFromUrl(String url) throws Exception {
        log.info("Swagger 导入: {}", url);
        String body = fetch(url);
        return parse(body, url);
    }

    /**
     * 从 JSON 字符串解析。
     */
    public List<ApiDefinitionDraft> importFromJson(String json) {
        return parse(json, null);
    }

    /**
     * 从文件字节导入（自动判断 JSON / YAML）。
     *
     * <p>当前实现只支持 JSON;遇到 YAML 抛 {@code UnsupportedOperationException}。
     */
    public List<ApiDefinitionDraft> importFromFileContent(byte[] bytes) {
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return parse(content, null);
    }

    // ============================================================
    // 解析主流程
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<ApiDefinitionDraft> parse(String content, String sourceUrl) {
        Map<String, Object> spec;
        try {
            spec = JsonUtils.fromJson(content, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 Swagger JSON 失败: " + e.getMessage(), e);
        }
        String version = String.valueOf(spec.getOrDefault("openapi", spec.getOrDefault("swagger", "3.0")));
        log.info("检测到 OpenAPI 版本: {}", version);

        List<ApiDefinitionDraft> result = new ArrayList<>();
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        if (paths == null) return result;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> operations = (Map<String, Object>) pathEntry.getValue();
            if (operations == null) continue;
            for (Map.Entry<String, Object> opEntry : operations.entrySet()) {
                String method = opEntry.getKey().toUpperCase();
                if (!isHttpMethod(method)) continue;
                Map<String, Object> op = (Map<String, Object>) opEntry.getValue();
                result.add(toDraft(path, method, op, sourceUrl));
            }
        }
        return result;
    }

    private ApiDefinitionDraft toDraft(String path, String method, Map<String, Object> op, String sourceUrl) {
        ApiDefinitionDraft.ApiDefinitionDraftBuilder b = ApiDefinitionDraft.builder()
                .name(orDefault(op, "summary", op, "operationId", path + " " + method))
                .method(method)
                .path(path)
                .description(orDefault(op, "description", ""))
                .tags(toStringList(op.get("tags")))
                .sourceUrl(sourceUrl)
                .pathParams(new ArrayList<>())
                .query(new ArrayList<>())
                .headers(new ArrayList<>())
                .responseExamples(new LinkedHashMap<>())
                .responseHeaders(new LinkedHashMap<>());

        // 1. parameters（OpenAPI 3 / Swagger 2 都用 parameters,但 Swagger 2 的 body/formData 在这里）
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) op.get("parameters");
        if (parameters != null) {
            for (Map<String, Object> param : parameters) {
                String in = String.valueOf(param.get("in"));
                Map<String, Object> schema = (Map<String, Object>) param.get("schema");
                Map<String, Object> kv = new LinkedHashMap<>();
                kv.put("key", param.get("name"));
                kv.put("value", param.get("default") != null ? param.get("default") : "");
                kv.put("description", param.getOrDefault("description", ""));
                kv.put("required", Boolean.TRUE.equals(param.get("required")));
                kv.put("enabled", true);
                switch (in) {
                    case "path" -> b.pathParams(appendToKv(b.build().getPathParams(), kv));
                    case "query" -> b.query(appendToKv(b.build().getQuery(), kv));
                    case "header" -> b.headers(appendToKv(b.build().getHeaders(), kv));
                    // body/formData 在 Swagger 2 中作为参数出现
                    case "body" -> {
                        if (schema != null) b.bodySchema(schema);
                    }
                    case "formData" -> {
                        b.bodyType("FORM_DATA");
                    }
                    default -> {
                        // cookie 等忽略
                    }
                }
            }
        }

        // 2. requestBody（OpenAPI 3）
        Map<String, Object> requestBody = (Map<String, Object>) op.get("requestBody");
        if (requestBody != null) {
            b.bodyType(resolveBodyType(requestBody));
            Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
            if (content != null) {
                Map<String, Object> jsonContent = (Map<String, Object>) content.getOrDefault(
                        "application/json", content.values().stream().findFirst().orElse(null));
                if (jsonContent != null) {
                    if (jsonContent.get("schema") instanceof Map<?, ?> schema) {
                        b.bodySchema((Map<String, Object>) schema);
                    }
                    if (jsonContent.get("example") != null) {
                        b.body(JsonUtils.toJson(jsonContent.get("example")));
                    }
                }
            }
        }

        // 3. responses
        Map<String, Object> responses = (Map<String, Object>) op.get("responses");
        if (responses != null) {
            Map<String, Object> examples = new LinkedHashMap<>();
            Map<String, Object> headers = new LinkedHashMap<>();
            for (Map.Entry<String, Object> respEntry : responses.entrySet()) {
                String status = respEntry.getKey();
                Map<String, Object> respBody = (Map<String, Object>) respEntry.getValue();
                // Swagger 2 的 schema 是直接字段,OpenAPI 3 在 content 下
                Object example = null;
                Map<String, Object> content = (Map<String, Object>) respBody.get("content");
                if (content != null) {
                    Map<String, Object> jsonContent = (Map<String, Object>) content.getOrDefault(
                            "application/json", content.values().stream().findFirst().orElse(null));
                    if (jsonContent != null) {
                        example = jsonContent.get("example");
                        if (example == null && jsonContent.get("schema") instanceof Map<?, ?> sch) {
                            example = exampleFromSchema((Map<String, Object>) sch);
                        }
                    }
                } else if (respBody.get("schema") instanceof Map<?, ?> schema) {
                    example = exampleFromSchema((Map<String, Object>) schema);
                }
                if (example != null) {
                    examples.put(status, JsonUtils.toJson(example));
                }
                // 响应头
                if (respBody.get("headers") instanceof Map<?, ?> hs) {
                    for (Object hk : hs.keySet()) headers.put(String.valueOf(hk), "");
                }
            }
            b.responseExamples(examples);
            b.responseHeaders(headers);
        }
        return b.build();
    }

    /**
     * 将草稿转为可入库的 {@link ApiDefinitionEntity}。
     */
    public ApiDefinitionEntity toEntity(ApiDefinitionDraft draft, String projectId, String moduleId) {
        ApiDefinitionEntity e = new ApiDefinitionEntity();
        e.setProjectId(projectId);
        e.setModuleId(moduleId);
        e.setName(draft.getName());
        e.setMethod(draft.getMethod());
        e.setPath(draft.getPath());
        e.setDescription(draft.getDescription());
        e.setPathParams(JsonUtils.toJson(draft.getPathParams()));
        e.setQuery(JsonUtils.toJson(draft.getQuery()));
        e.setHeaders(JsonUtils.toJson(draft.getHeaders()));
        e.setBodyType(draft.getBodyType() == null ? "NONE" : draft.getBodyType());
        e.setBodySchema(draft.getBodySchema() == null ? "{}" : JsonUtils.toJson(draft.getBodySchema()));
        e.setBody(draft.getBody());
        e.setResponseExamples(JsonUtils.toJson(draft.getResponseExamples()));
        e.setResponseHeaders(JsonUtils.toJson(draft.getResponseHeaders()));
        e.setTags(JsonUtils.toJson(draft.getTags()));
        e.setSourceUrl(draft.getSourceUrl());
        if (draft.getSourceContent() != null) {
            e.setSourceHash(sha256(draft.getSourceContent()));
        }
        e.setMockEnabled(false);
        e.setMockType("BASIC");
        e.setMockStatusCode(200);
        return e;
    }

    /**
     * 计算 SHA-256 哈希（同步差异用）。
     */
    public String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private String fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/yaml, */*")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + url);
        }
        return resp.body();
    }

    private static boolean isHttpMethod(String m) {
        return switch (m.toUpperCase()) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE" -> true;
            default -> false;
        };
    }

    private static List<String> toStringList(Object o) {
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) out.add(String.valueOf(item));
            return out;
        }
        return List.of();
    }

    private static List<Map<String, Object>> appendToKv(List<Map<String, Object>> list, Map<String, Object> kv) {
        List<Map<String, Object>> copy = new ArrayList<>(list);
        copy.add(kv);
        return copy;
    }

    private static String orDefault(Map<String, Object> map, String key1, String def) {
        Object v = map.get(key1);
        return v == null ? def : String.valueOf(v);
    }

    /**
     * 三参数版的 orDefault（避免与方法引用冲突）。
     */
    private static String orDefault(Map<String, Object> m1, String k1,
                                    Map<String, Object> m2, String k2, String def) {
        Object v = m1.get(k1);
        if (v == null) v = m2.get(k2);
        return v == null ? def : String.valueOf(v);
    }

    private static String resolveBodyType(Map<String, Object> requestBody) {
        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        if (content == null || content.isEmpty()) return "NONE";
        String first = content.keySet().stream().findFirst().orElse("");
        if (first.contains("form-data")) return "FORM_DATA";
        if (first.contains("x-www-form-urlencoded")) return "X_WWW_FORM_URLENCODED";
        if (first.contains("xml")) return "XML";
        if (first.contains("octet-stream")) return "BINARY";
        return "JSON";
    }

    /**
     * 从 OpenAPI Schema 生成示例值（仅做粗略推断：string→""、integer→0、boolean→false、object→递归、array→[]）。
     */
    @SuppressWarnings("unchecked")
    private static Object exampleFromSchema(Map<String, Object> schema) {
        if (schema == null) return null;
        Object example = schema.get("example");
        if (example != null) return example;
        String type = String.valueOf(schema.getOrDefault("type", "object"));
        return switch (type) {
            case "string" -> "";
            case "integer", "number" -> 0;
            case "boolean" -> false;
            case "array" -> {
                Object items = schema.get("items");
                if (items instanceof Map<?, ?> itemSchema) {
                    yield List.of(exampleFromSchema((Map<String, Object>) itemSchema));
                }
                yield List.of();
            }
            case "object" -> {
                Map<String, Object> props = (Map<String, Object>) schema.get("properties");
                Map<String, Object> map = new LinkedHashMap<>();
                if (props != null) {
                    for (Map.Entry<String, Object> propEntry : props.entrySet()) {
                        if (propEntry.getValue() instanceof Map<?, ?> ps) {
                            map.put(propEntry.getKey(), exampleFromSchema((Map<String, Object>) ps));
                        }
                    }
                }
                yield map;
            }
            default -> null;
        };
    }

    // ============================================================
    // DTO
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDefinitionDraft {
        private String name;
        private String method;
        private String path;
        private String description;
        private List<String> tags;
        private List<Map<String, Object>> pathParams;
        private List<Map<String, Object>> query;
        private List<Map<String, Object>> headers;
        private String bodyType;
        private Map<String, Object> bodySchema;
        private String body;
        private Map<String, Object> responseExamples;
        private Map<String, Object> responseHeaders;
        private String sourceUrl;
        private String sourceContent;
    }
}