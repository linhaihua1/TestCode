package com.apiweb.engine;

import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用例执行器。
 *
 * <p>按顺序执行用例中的步骤，支持 HTTP 请求 + 4 类流程控制：
 * <ul>
 *   <li>{@code http}：单次 HTTP 请求（可带断言 / 提取）</li>
 *   <li>{@code if}：条件分支</li>
 *   <li>{@code for}：计数循环</li>
 *   <li>{@code while}：条件循环（带超时和最大迭代保护）</li>
 *   <li>{@code script}：执行 JS 脚本</li>
 * </ul>
 *
 * <h3>步骤 JSON 约定</h3>
 * 每个步骤是一个 Map，序列化到数据库 t_case.steps 字段。type 决定处理方式：
 * <pre>
 *   {"type":"http", "name":"登录", "method":"POST", "url":"{{baseUrl}}/login",
 *    "headers":[...], "query":[...], "body":"...", "assertions":[...], "extracts":[...]}
 *
 *   {"type":"if",     "condition":"var == 'x'", "children":[step, ...]}
 *   {"type":"for",    "loopCount":3, "children":[step, ...]}
 *   {"type":"while",  "condition":"var < 10", "timeoutMs":30000, "children":[step, ...]}
 *   {"type":"script", "language":"javascript", "script":"..."}
 * </pre>
 *
 * <h3>JS 引擎</h3>
 * JDK 17 默认无 Nashorn，{@link ScriptEngineManager#getEngineByName(String) "js"} 可能为 null。
 * 引入 {@code org.graalvm.polyglot:js} 或 {@code org.openjdk.nashorn:nashorn-core} 即可启用完整 JS 语法。
 * 无 JS 引擎时，条件表达式会降级为简单 {@code "var op value"} 解析。
 *
 * <h3>线程安全</h3>
 * 本类无状态，作为 Spring 单例 Bean 可被多线程共用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseRunner {

    /**
     * WHILE 循环最大迭代次数（防死循环硬上限）。
     * 即使 condition 一直为 true，达到这个次数也会强制退出。
     */
    private static final int MAX_WHILE_ITERATIONS = 1000;

    private final HttpExecutor httpExecutor;
    private final AssertEvaluator assertEvaluator;
    private final Extractor extractor;

    /**
     * 执行完整用例。
     *
     * @param stepsJson t_case.steps 字段（JSON 数组字符串）
     * @param resolver  入口变量池（已包含环境变量 + 全局变量）
     * @return 完整执行结果（含所有步骤、断言、提取、最终变量快照）
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
            // 任一步骤非 success，整体为 failed
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

    /**
     * 递归执行步骤列表（被 IF/FOR/WHILE 的 children 复用）。
     *
     * @param steps    待执行步骤
     * @param resolver 共享的变量池（递归间共享）
     * @param result   执行结果（累积步骤）
     */
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
                    // IF 分支：condition 为 true 执行 children[0]
                    boolean condition = evalCondition(str(raw.get("condition"), "true"), resolver);
                    if (condition) {
                        runSteps((List<Map<String, Object>>) raw.getOrDefault("children", List.of()),
                                resolver, result);
                    }
                }
                case "for" -> {
                    // FOR 循环：loopCount 次，每次注入 __index__ 变量（0-based）
                    int count = raw.get("loopCount") instanceof Number n ? n.intValue() : 1;
                    for (int i = 0; i < count; i++) {
                        resolver.put("__index__", String.valueOf(i));
                        runSteps((List<Map<String, Object>>) raw.getOrDefault("children", List.of()),
                                resolver, result);
                    }
                }
                case "while" -> {
                    // WHILE 循环：condition 为 true 时重复，timeoutMs 控制最长时长
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

    /**
     * 执行单个 HTTP 步骤：发送请求 → 评估断言 → 提取变量 → 记录摘要。
     */
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

            // 1. 断言评估：任一失败则步骤失败
            List<EngineDtos.AssertionResult> assertionResults =
                    assertEvaluator.evaluateAll(step.getAssertions(),
                            new AssertEvaluator.EvalInput(resp.status(),
                                    HttpExecutor.lowercaseHeaders(resp.headers()),
                                    resp.body(), resp.durationMs()));
            sr.setAssertions(assertionResults);
            if (assertionResults.stream().anyMatch(a -> !a.isPassed())) {
                sr.setStatus("failed");
            }

            // 2. 提取变量：写入 resolver（供后续步骤用）和 result.variables（用于报告展示）
            for (EngineDtos.Extract e : step.getExtracts()) {
                EngineDtos.ExtractResult er = extractor.extract(e,
                        new Extractor.Response(resp.status(),
                                HttpExecutor.lowercaseHeaders(resp.headers()), resp.body()));
                sr.getExtracts().add(er);
                resolver.put(er.getVariable(), er.getValue());
                result.getVariables().put(er.getVariable(), er.getValue());
            }
            // 3. 请求 / 响应摘要（body 截断到 1KB，避免报告过大）
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
            // 网络异常 / URL 错误等都归为 error（区别于断言失败的 failed）
            sr.setStatus("error");
            sr.setError(e.getMessage());
            sr.setDurationMs(System.currentTimeMillis() - start);
        }
        result.getSteps().add(sr);
    }

    /**
     * JS 脚本步骤执行。
     *
     * <p>优先尝试用 JDK 的 JS 引擎（GraalVM JS / Nashorn）执行；无引擎时步骤标记为 failed，
     * 但不阻断用例整体（用户可手动引入 {@code org.graalvm.polyglot:js}）。
     *
     * <p>执行上下文：所有变量（解析器中的所有键值对）作为 JS 全局变量注入，
     * 脚本中可直接写 {@code variables.token = 'xxx';} 修改变量。
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
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
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
     * 条件表达式求值。
     *
     * <p>策略：
     * <ol>
     *   <li>优先用 JS 引擎执行完整 JS 表达式</li>
     *   <li>无 JS 引擎时降级为简单 {@code "var op value"} 解析
     *       （支持 {@code == != > >= < <=}，变量名为 {{var}} 内容或裸字符串）</li>
     * </ol>
     *
     * @return 布尔结果，表达式异常或无法解析时返回 false
     */
    private boolean evalCondition(String condition, VariablesResolver resolver) {
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
            if (engine != null) {
                resolver.all().forEach(engine::put);
                Object ret = engine.eval(condition);
                return Boolean.TRUE.equals(ret);
            }
        } catch (Exception ignored) {
            // JS 引擎执行失败，降级
        }
        // 降级：解析 "变量 == 值" / "变量 != 值" / "true"/"false"
        String c = condition.trim();
        if ("true".equals(c)) return true;
        if ("false".equals(c)) return false;
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

    /** null-safe 转 double，失败返回 0 */
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

    /** 把 Map 列表转换为 KV 结构（用于 Headers / Query） */
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

    /** 把 Map 列表转换为 Assertion 结构 */
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

    /** 把 Map 列表转换为 Extract 结构 */
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
