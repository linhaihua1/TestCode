package com.apiweb.engine;

import com.apiweb.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 断言评估器。
 *
 * <p>对一次 HTTP 请求的响应按规则做断言校验，每条断言独立评估，最终聚合为 {@link EngineDtos.AssertionResult} 列表。
 *
 * <h3>支持的数据源（{@code source} 字段）</h3>
 * <table>
 *   <tr><th>source</th><th>property</th><th>取到的值</th></tr>
 *   <tr><td>status</td><td>（留空）</td><td>HTTP 状态码（int 转 String）</td></tr>
 *   <tr><td>responsetime</td><td>（留空）</td><td>响应耗时 ms</td></tr>
 *   <tr><td>header</td><td>Header 名（小写）</td><td>Header 的首个值</td></tr>
 *   <tr><td>body</td><td>（留空）</td><td>响应原始 body</td></tr>
 *   <tr><td>jsonpath</td><td>JSONPath 路径</td><td>路径对应的值</td></tr>
 * </table>
 *
 * <h3>支持的运算符（{@code operator} 字段）</h3>
 * <ul>
 *   <li>{@code equals} / {@code not_equals}：字符串相等比较</li>
 *   <li>{@code contains} / {@code not_contains}：字符串包含（支持中文、特殊字符）</li>
 *   <li>{@code regex}：正则匹配（{@link java.util.regex.Pattern#find()} 行为）</li>
 *   <li>{@code gt} / {@code lt} / {@code gte} / {@code lte}：数值比较（无法解析为数字时按 0 处理）</li>
 *   <li>{@code empty} / {@code not_empty}：判空</li>
 * </ul>
 */
@Component
public class AssertEvaluator {

    /**
     * 单条断言的输入数据：响应状态码、Headers、Body、响应耗时。
     */
    public record EvalInput(int status, Map<String, List<String>> headers, String body, long responseTimeMs) {}

    /**
     * 评估单条断言。
     *
     * @param a     断言定义
     * @param input 响应数据
     * @return 评估结果（含实际值、是否通过、失败原因）
     */
    public EngineDtos.AssertionResult evaluate(EngineDtos.Assertion a, EvalInput input) {
        String actual = extractActual(a, input);
        boolean passed = applyOperator(a.getOperator(), a.getExpected(), actual, input);
        String message = passed ? "通过"
                : "断言失败: " + describe(a) + ", 实际值: " + truncate(actual);
        return EngineDtos.AssertionResult.builder()
                .source(a.getSource())
                .property(a.getProperty())
                .operator(a.getOperator())
                .expected(a.getExpected())
                .actual(truncate(actual))
                .passed(passed)
                .message(message)
                .build();
    }

    /**
     * 批量评估多条断言。
     *
     * <p>某条断言抛异常会被捕获，对应结果标记为失败（message 含异常文本），不影响其它断言评估。
     */
    public List<EngineDtos.AssertionResult> evaluateAll(List<EngineDtos.Assertion> assertions, EvalInput input) {
        List<EngineDtos.AssertionResult> results = new ArrayList<>();
        if (assertions == null) {
            return results;
        }
        for (EngineDtos.Assertion a : assertions) {
            try {
                results.add(evaluate(a, input));
            } catch (Exception e) {
                results.add(EngineDtos.AssertionResult.builder()
                        .source(a.getSource())
                        .operator(a.getOperator())
                        .passed(false)
                        .message("断言执行异常: " + e.getMessage())
                        .build());
            }
        }
        return results;
    }

    /**
     * 从响应中按 source 取出实际值。
     */
    private String extractActual(EngineDtos.Assertion a, EvalInput input) {
        String source = a.getSource() == null ? "body" : a.getSource().toLowerCase();
        return switch (source) {
            case "status" -> String.valueOf(input.status());
            case "responsetime" -> String.valueOf(input.responseTimeMs());
            case "header" -> {
                List<String> values = input.headers() == null ? null
                        : input.headers().get(a.getProperty() == null ? "" : a.getProperty().toLowerCase());
                yield values == null || values.isEmpty() ? "" : values.get(0);
            }
            case "jsonpath" -> jsonPath(input.body(), a.getProperty());
            default -> input.body() == null ? "" : input.body();
        };
    }

    /**
     * 应用运算符比较 expected 与 actual，返回是否通过。
     *
     * @param op       运算符
     * @param expected 期望值（来自断言定义）
     * @param actual   实际值（来自响应）
     * @param input    响应数据（目前未用，预留给更复杂运算符）
     */
    private boolean applyOperator(String op, String expected, String actual, EvalInput input) {
        op = op == null ? "equals" : op.toLowerCase();
        return switch (op) {
            case "equals" -> safeEquals(expected, actual);
            case "not_equals" -> !safeEquals(expected, actual);
            case "contains" -> actual != null && actual.contains(expected == null ? "" : expected);
            case "not_contains" -> actual == null || !actual.contains(expected == null ? "" : expected);
            case "regex" -> actual != null && Pattern.compile(expected == null ? "" : expected)
                    .matcher(actual).find();
            case "gt" -> toNumber(actual) > toNumber(expected);
            case "lt" -> toNumber(actual) < toNumber(expected);
            case "gte" -> toNumber(actual) >= toNumber(expected);
            case "lte" -> toNumber(actual) <= toNumber(expected);
            case "empty" -> actual == null || actual.isBlank();
            case "not_empty" -> actual != null && !actual.isBlank();
            default -> false;
        };
    }

    /**
     * 轻量级 JSONPath 实现（仅支持本系统实际用到的语法子集）：
     * <ul>
     *   <li>{@code $.a.b}：嵌套对象</li>
     *   <li>{@code $.a[0]}：数组下标</li>
     *   <li>{@code $.a[0].b}：对象 + 数组混合</li>
     * </ul>
     * <p>不支持：过滤器（{@code [?(@.price>10)]}）、通配符（{@code *..price}）、函数（{@code length()}）等高级特性。
     * 如需完整实现，建议引入 {@code com.jayway.jsonpath} 依赖。</p>
     *
     * @param body JSON 字符串
     * @param path JSONPath 表达式（必须以 {@code $} 开头）
     * @return 命中值（toString），未命中返回 ""
     */
    @SuppressWarnings("unchecked")
    private String jsonPath(String body, String path) {
        if (body == null || path == null || !path.startsWith("$")) {
            return "";
        }
        try {
            Object current = JsonUtils.fromJson(body, Object.class);
            String[] segments = path.substring(1).split("\\.");
            for (String raw : segments) {
                if (raw.isEmpty()) {
                    continue;
                }
                String segment = raw;
                int index = -1;
                int bracket = segment.indexOf('[');
                if (bracket >= 0 && segment.endsWith("]")) {
                    index = Integer.parseInt(segment.substring(bracket + 1, segment.length() - 1));
                    segment = segment.substring(0, bracket);
                }
                if (!segment.isEmpty()) {
                    if (current instanceof Map<?, ?> map) {
                        current = map.get(segment);
                    } else {
                        return "";
                    }
                }
                if (index >= 0) {
                    if (current instanceof List<?> list && index < list.size()) {
                        current = list.get(index);
                    } else {
                        return "";
                    }
                }
            }
            return current == null ? "" : String.valueOf(current);
        } catch (Exception e) {
            return "";
        }
    }

    /** null-safe 字符串相等 */
    private boolean safeEquals(String expected, String actual) {
        return (expected == null ? "" : expected).equals(actual == null ? "" : actual);
    }

    /** null-safe 转 double，失败返回 0 */
    private double toNumber(String value) {
        try {
            return Double.parseDouble(value == null ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 生成"断言失败: [source:property] operator 'expected'" 形式的人类可读描述 */
    private String describe(EngineDtos.Assertion a) {
        return "[" + a.getSource() + (a.getProperty() != null ? ":" + a.getProperty() : "")
                + "] " + a.getOperator() + " '" + a.getExpected() + "'";
    }

    /** 截断过长的实际值，避免报告 / 日志爆掉 */
    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) + "...(截断)" : value;
    }
}
