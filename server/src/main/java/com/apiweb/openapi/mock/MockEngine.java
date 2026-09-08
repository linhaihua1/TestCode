package com.apiweb.openapi.mock;

import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.*;

/**
 * Mock 引擎（按需求文档 §5.4 Mock 服务 - 四档策略）。
 *
 * <h3>Mock 类型</h3>
 * <ul>
 *   <li>{@code BASIC}：直接返回 {@link ApiDefinitionEntity#getMockResponse()}</li>
 *   <li>{@code CONDITIONAL}：遍历 mockRules,首条 when 匹配的作为响应</li>
 *   <li>{@code DYNAMIC}：执行 JS 脚本,返回值即响应体(支持同步/异步)</li>
 *   <li>{@code SMART}：根据请求 method+path 在 responseExamples 中自动选最匹配的状态码</li>
 * </ul>
 *
 * <h3>使用</h3>
 * <pre>{@code
 *   MockResponse resp = mockEngine.execute(apiDefinition, "POST", "/login",
 *           Map.of("user", "admin"), "{}");
 * }</pre>
 */
@Slf4j
@Component
public class MockEngine {

    /**
     * Mock 响应：状态码、响应头、响应体、延迟(毫秒,仅 SMART/BASIC 用)。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockResponse {
        private int status;
        private Map<String, String> headers;
        private String body;
    }

    /**
     * 执行 Mock。
     *
     * @param api       接口定义(mockEnabled 必须为 true)
     * @param method    请求方法
     * @param path      请求路径
     * @param query     Query 参数
     * @param body      请求体
     * @return Mock 响应
     */
    public MockResponse execute(ApiDefinitionEntity api, String method, String path,
                                Map<String, List<String>> query, String body) {
        if (api == null) {
            return MockResponse.builder().status(404)
                    .body("{\"error\":\"API not found\"}")
                    .headers(Map.of("Content-Type", "application/json"))
                    .build();
        }
        if (!Boolean.TRUE.equals(api.getMockEnabled())) {
            return MockResponse.builder().status(404)
                    .body("{\"error\":\"Mock not enabled for this API\"}")
                    .headers(Map.of("Content-Type", "application/json"))
                    .build();
        }
        String type = api.getMockType() == null ? "BASIC" : api.getMockType().toUpperCase();
        return switch (type) {
            case "CONDITIONAL" -> conditional(api, query, body);
            case "DYNAMIC" -> dynamic(api, method, path, query, body);
            case "SMART" -> smart(api, method, path);
            default -> basic(api);
        };
    }

    // ============================================================
    // 四档策略实现
    // ============================================================

    private MockResponse basic(ApiDefinitionEntity api) {
        return MockResponse.builder()
                .status(api.getMockStatusCode() == null ? 200 : api.getMockStatusCode())
                .headers(Map.of("Content-Type", "application/json"))
                .body(api.getMockResponse() == null ? "{}" : api.getMockResponse())
                .build();
    }

    @SuppressWarnings("unchecked")
    private MockResponse conditional(ApiDefinitionEntity api, Map<String, List<String>> query, String body) {
        String rulesJson = api.getMockRules();
        if (rulesJson == null || rulesJson.isBlank()) return basic(api);
        List<Map<String, Object>> rules = JsonUtils.fromJson(rulesJson, List.class);
        if (rules == null || rules.isEmpty()) return basic(api);
        for (Map<String, Object> rule : rules) {
            Map<String, Object> when = (Map<String, Object>) rule.get("when");
            if (when == null || matchCondition(when, query, body)) {
                return MockResponse.builder()
                        .status(rule.get("status") instanceof Number n ? n.intValue() :
                                (api.getMockStatusCode() == null ? 200 : api.getMockStatusCode()))
                        .headers(Map.of("Content-Type", "application/json"))
                        .body(rule.get("response") == null ? "{}" : JsonUtils.toJson(rule.get("response")))
                        .build();
            }
        }
        // 没匹配上,返回基本响应
        return basic(api);
    }

    private MockResponse dynamic(ApiDefinitionEntity api, String method, String path,
                                 Map<String, List<String>> query, String body) {
        String script = api.getMockScript();
        if (script == null || script.isBlank()) return basic(api);
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
            if (engine == null) {
                log.warn("DYNAMIC Mock 需要 JS 引擎,建议引入 org.graalvm.polyglot:js");
                return basic(api);
            }
            engine.put("method", method);
            engine.put("path", path);
            engine.put("query", toFlat(query));
            engine.put("body", body == null ? "" : body);
            engine.put("api", api);
            Object ret = engine.eval(script);
            return MockResponse.builder()
                    .status(api.getMockStatusCode() == null ? 200 : api.getMockStatusCode())
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(ret == null ? "{}" : String.valueOf(ret))
                    .build();
        } catch (Exception e) {
            log.warn("DYNAMIC Mock 脚本执行失败: {}", e.getMessage());
            return MockResponse.builder()
                    .status(500)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body("{\"error\":\"Mock script error: " + escapeJson(e.getMessage()) + "\"}")
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private MockResponse smart(ApiDefinitionEntity api, String method, String path) {
        Map<String, Object> examples = new HashMap<>();
        if (api.getResponseExamples() != null && !api.getResponseExamples().isBlank()) {
            try {
                examples = JsonUtils.fromJson(api.getResponseExamples(), Map.class);
            } catch (Exception ignored) {}
        }
        if (examples.isEmpty()) return basic(api);
        // 优先级：200 → 2xx → default → 第一个
        String picked = null;
        if (examples.containsKey("200")) picked = "200";
        else if (examples.containsKey("default")) picked = "default";
        else {
            for (String k : examples.keySet()) {
                if (k.startsWith("2")) { picked = k; break; }
            }
        }
        if (picked == null) picked = examples.keySet().stream().findFirst().orElse("default");
        Object body = examples.get(picked);
        return MockResponse.builder()
                .status(picked.equals("default") ? 200 : Integer.parseInt(picked))
                .headers(Map.of("Content-Type", "application/json"))
                .body(body == null ? "{}" : String.valueOf(body))
                .build();
    }

    // ============================================================
    // 工具
    // ============================================================

    /**
     * 条件匹配：所有 when 条件 AND 起来,任一失败则规则不匹配。
     *
     * <p>支持的 condition 格式：
     * <pre>{key: "user", op: "equals", value: "admin"}</pre>
     * op 支持：equals / not_equals / contains / exists
     */
    private boolean matchCondition(Map<String, Object> when, Map<String, List<String>> query, String body) {
        for (Map.Entry<String, Object> entry : when.entrySet()) {
            String key = entry.getKey();
            Object cond = entry.getValue();
            if (!(cond instanceof Map<?, ?> condMapRaw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> condMap = (Map<String, Object>) condMapRaw;
            String operator = String.valueOf(condMap.getOrDefault("op", "equals"));
            Object expected = condMap.get("value");
            String actual = pickValue(key, query, body);
            boolean ok = switch (operator.toLowerCase()) {
                case "not_equals" -> !String.valueOf(expected).equals(actual);
                case "contains" -> actual.contains(String.valueOf(expected));
                case "exists" -> actual != null && !actual.isEmpty();
                default -> String.valueOf(expected).equals(actual);
            };
            if (!ok) return false;
        }
        return true;
    }

    private String pickValue(String key, Map<String, List<String>> query, String body) {
        // 1. query
        List<String> qv = query == null ? null : query.get(key);
        if (qv != null && !qv.isEmpty()) return qv.get(0);
        // 2. body(JSON)
        if (body != null && !body.isBlank() && body.startsWith("{")) {
            try {
                Object parsed = JsonUtils.fromJson(body, Map.class);
                if (parsed instanceof Map<?, ?> m) {
                    Object v = ((Map<String, Object>) m).get(key);
                    if (v != null) return String.valueOf(v);
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static Map<String, String> toFlat(Map<String, List<String>> query) {
        Map<String, String> flat = new LinkedHashMap<>();
        if (query == null) return flat;
        query.forEach((k, v) -> flat.put(k, v == null || v.isEmpty() ? "" : v.get(0)));
        return flat;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}