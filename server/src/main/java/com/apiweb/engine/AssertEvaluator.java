package com.apiweb.engine;

import com.apiweb.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 断言评估器：支持 status / header / body / jsonpath / responsetime 五种来源，
 * equals / contains / regex / gt / lt / empty 等操作符。
 */
@Component
public class AssertEvaluator {

    public record EvalInput(int status, Map<String, List<String>> headers, String body, long responseTimeMs) {}

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
     * 轻量 JSONPath 子集：$.a.b / $.a[0].b / $.a[0]。
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

    private boolean safeEquals(String expected, String actual) {
        return (expected == null ? "" : expected).equals(actual == null ? "" : actual);
    }

    private double toNumber(String value) {
        try {
            return Double.parseDouble(value == null ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String describe(EngineDtos.Assertion a) {
        return "[" + a.getSource() + (a.getProperty() != null ? ":" + a.getProperty() : "")
                + "] " + a.getOperator() + " '" + a.getExpected() + "'";
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) + "...(截断)" : value;
    }
}
