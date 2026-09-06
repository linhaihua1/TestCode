package com.apiweb.engine;

import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用例执行器：按步骤顺序执行用例，支持 IF / FOR / WHILE 流程控制器。
 * 步骤 JSON 约定（每步为对象）：
 *   {"type":"http", "name":"登录", "method":"POST", "url":"{{baseUrl}}/login",
 *    "headers":[...], "query":[...], "body":"...", "assertions":[...], "extracts":[...]}
 *   {"type":"if",     "condition":"js 表达式", "children":[...]}
 *   {"type":"for",    "loopCount":3, "children":[...]}
 *   {"type":"while",  "condition":"js 表达式", "timeoutMs":30000, "children":[...]}
 *   {"type":"script", "language":"javascript", "script":"..."}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseRunner {

    private static final int MAX_WHILE_ITERATIONS = 1000;

    private final HttpExecutor httpExecutor;
    private final AssertEvaluator assertEvaluator;
    private final Extractor extractor;

    /**
     * 执行完整用例（stepsJson 为 t_case.steps 的 JSON 数组字符串）。
     */
    @SuppressWarnings("unchecked")
    public EngineDtos.ExecutionResult run(String stepsJson, VariablesResolver resolver) {
        long start = System.currentTimeMillis();
        EngineDtos.ExecutionResult result = EngineDtos.ExecutionResult.builder()
                .result("success")
                .build();
        List<Map<String, Object>> steps = JsonUtils.fromJson(stepsJson == null ? "[]" : stepsJson,
                List.class);
        try {
            runSteps(steps, resolver, result);
            if (result.getSteps().stream().anyMatch(s -> !"success".equals(s.getStatus()))) {
                result.setResult("failed");
            }
        } catch (Exception e) {
            log.error("用例执行异常", e);
            result.setResult("error");
            result.setErrorLog(e.getMessage());
        }
        result.setTotalDurationMs(System.currentTimeMillis() - start);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void runSteps(List<Map<String, Object>> steps, VariablesResolver resolver,
                          EngineDtos.ExecutionResult result) throws Exception {
        if (steps == null) {
            return;
        }
        for (Map<String, Object> raw : steps) {
            String type = str(raw.get("type"), "http");
            switch (type) {
                case "if" -> {
                    boolean condition = evalCondition(str(raw.get("condition"), "true"), resolver);
                    if (condition) {
                        runSteps((List<Map<String, Object>>) raw.getOrDefault("children", List.of()),
                                resolver, result);
                    }
                }
                case "for" -> {
                    int count = raw.get("loopCount") instanceof Number n ? n.intValue() : 1;
                    for (int i = 0; i < count; i++) {
                        resolver.put("__index__", String.valueOf(i));
                        runSteps((List<Map<String, Object>>) raw.getOrDefault("children", List.of()),
                                resolver, result);
                    }
                }
                case "while" -> {
                    long timeoutMs = raw.get("timeoutMs") instanceof Number n ? n.longValue() : 30000L;
                    long deadline = System.currentTimeMillis() + timeoutMs;
                    int iterations = 0;
                    while (evalCondition(str(raw.get("condition"), "false"), resolver)) {
                        if (++iterations > MAX_WHILE_ITERATIONS
                                || System.currentTimeMillis() > deadline) {
                            throw new IllegalStateException("WHILE 循环超限/超时");
                        }
                        runSteps((List<Map<String, Object>>) raw.getOrDefault("children", List.of()),
                                resolver, result);
                    }
                }
                case "script" -> runScriptStep(raw, resolver, result);
                default -> runHttpStep(raw, resolver, result);
            }
        }
    }

    private void runHttpStep(Map<String, Object> raw, VariablesResolver resolver,
                             EngineDtos.ExecutionResult result) {
        EngineDtos.HttpStep step = new EngineDtos.HttpStep(
                str(raw.get("name"), "未命名步骤"),
                str(raw.get("method"), "GET"),
                str(raw.get("url"), ""),
                toKVList(raw.get("headers")),
                toKVList(raw.get("query")),
                str(raw.get("body"), null),
                toAssertionList(raw.get("assertions")),
                toExtractList(raw.get("extracts")));
        long start = System.currentTimeMillis();
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status("success")
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .build();
        try {
            HttpExecutor.Response resp = httpExecutor.execute(step, resolver);
            sr.setDurationMs(resp.durationMs());

            // 断言
            List<EngineDtos.AssertionResult> assertionResults =
                    assertEvaluator.evaluateAll(step.getAssertions(),
                            new AssertEvaluator.EvalInput(resp.status(),
                                    HttpExecutor.lowercaseHeaders(resp.headers()),
                                    resp.body(), resp.durationMs()));
            sr.setAssertions(assertionResults);
            if (assertionResults.stream().anyMatch(a -> !a.isPassed())) {
                sr.setStatus("failed");
            }

            // 提取
            for (EngineDtos.Extract e : step.getExtracts()) {
                EngineDtos.ExtractResult er = extractor.extract(e,
                        new Extractor.Response(resp.status(),
                                HttpExecutor.lowercaseHeaders(resp.headers()), resp.body()));
                sr.getExtracts().add(er);
                resolver.put(er.getVariable(), er.getValue());
                result.getVariables().put(er.getVariable(), er.getValue());
            }
            sr.setRequestSummary(JsonUtils.toJson(Map.of(
                    "method", step.getMethod(),
                    "url", resolver.resolve(step.getUrl()))));
            String bodyPreview = resp.body() == null ? "" :
                    (resp.body().length() > 1024 ? resp.body().substring(0, 1024) + "..." : resp.body());
            sr.setResponseSummary(JsonUtils.toJson(Map.of(
                    "status", resp.status(),
                    "durationMs", resp.durationMs(),
                    "body", bodyPreview)));
        } catch (Exception e) {
            sr.setStatus("error");
            sr.setError(e.getMessage());
            sr.setDurationMs(System.currentTimeMillis() - start);
        }
        result.getSteps().add(sr);
    }

    /**
     * JS 脚本步骤：通过 Nashorn/禁用时的降级处理——仅支持变量赋值类简单脚本。
     * JDK17 无内置 JS 引擎时，脚本以注释形式记录，不阻断执行。
     */
    private void runScriptStep(Map<String, Object> raw, VariablesResolver resolver,
                               EngineDtos.ExecutionResult result) {
        String script = str(raw.get("script"), "");
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(str(raw.get("name"), "脚本步骤"))
                .status("success")
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .build();
        try {
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("js");
            if (engine == null) {
                sr.setStatus("failed");
                sr.setError("当前 JVM 无可用 JS 引擎（建议引入 org.graalvm.polyglot:js 依赖）");
            } else {
                resolver.all().forEach(engine::put);
                Object ret = engine.eval(script);
                if (ret != null) {
                    resolver.put("__script_result__", String.valueOf(ret));
                    result.getVariables().put("__script_result__", String.valueOf(ret));
                }
            }
        } catch (Exception e) {
            sr.setStatus("error");
            sr.setError("脚本执行失败: " + e.getMessage());
        }
        result.getSteps().add(sr);
    }

    /**
     * 条件表达式求值：支持纯 JS（有引擎时）；降级支持简单比较 "var op value"。
     */
    private boolean evalCondition(String condition, VariablesResolver resolver) {
        try {
            javax.script.ScriptEngine engine =
                    new javax.script.ScriptEngineManager().getEngineByName("js");
            if (engine != null) {
                resolver.all().forEach(engine::put);
                Object ret = engine.eval(condition);
                return Boolean.TRUE.equals(ret);
            }
        } catch (Exception ignored) {
        }
        // 降级：解析 "变量 == 值" / "变量 != 值" / "true"/"false"
        String c = condition.trim();
        if ("true".equals(c)) {
            return true;
        }
        if ("false".equals(c)) {
            return false;
        }
        for (String op : new String[]{"==", "!=", ">=", "<=", ">", "<"}) {
            int idx = c.indexOf(op);
            if (idx > 0) {
                String left = resolver.resolve(c.substring(0, idx).trim()
                        .replace("{{", "").replace("}}", ""));
                String right = c.substring(idx + op.length()).trim().replace("\"", "");
                return switch (op) {
                    case "==" -> left.equals(right);
                    case "!=" -> !left.equals(right);
                    case ">" -> toD(left) > toD(right);
                    case "<" -> toD(left) < toD(right);
                    case ">=" -> toD(left) >= toD(right);
                    default -> toD(left) <= toD(right);
                };
            }
        }
        return false;
    }

    private double toD(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static List<EngineDtos.KV> toKVList(Object o) {
        List<EngineDtos.KV> list = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    list.add(new EngineDtos.KV(
                            str(m.get("key"), ""),
                            str(m.get("value"), ""),
                            m.get("enabled") instanceof Boolean b ? b : true));
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<EngineDtos.Assertion> toAssertionList(Object o) {
        List<EngineDtos.Assertion> list = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    list.add(new EngineDtos.Assertion(
                            str(m.get("source"), "body"),
                            str(m.get("property"), null),
                            str(m.get("operator"), "equals"),
                            m.get("expected") == null ? null : String.valueOf(m.get("expected"))));
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<EngineDtos.Extract> toExtractList(Object o) {
        List<EngineDtos.Extract> list = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    list.add(new EngineDtos.Extract(
                            str(m.get("type"), "jsonpath"),
                            str(m.get("expression"), ""),
                            str(m.get("variable"), "")));
                }
            }
        }
        return list;
    }
}
