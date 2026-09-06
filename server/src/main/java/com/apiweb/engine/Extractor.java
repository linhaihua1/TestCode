package com.apiweb.engine;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提取器：从响应中提取变量（jsonpath / regex / header），供后续步骤引用。
 */
@Component
public class Extractor {

    public record Response(int status, Map<String, List<String>> headers, String body) {}

    public EngineDtos.ExtractResult extract(EngineDtos.Extract e, Response response) {
        String value = switch (e.getType() == null ? "jsonpath" : e.getType().toLowerCase()) {
            case "regex" -> extractByRegex(e.getExpression(), response.body());
            case "header" -> extractHeader(e.getExpression(), response.headers());
            default -> extractByJsonPath(e.getExpression(), response.body());
        };
        return EngineDtos.ExtractResult.builder()
                .variable(e.getVariable())
                .value(value)
                .build();
    }

    private String extractByRegex(String expression, String body) {
        if (expression == null || body == null) {
            return "";
        }
        Matcher matcher = Pattern.compile(expression, Pattern.DOTALL).matcher(body);
        if (matcher.find()) {
            return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
        }
        return "";
    }

    private String extractHeader(String name, Map<String, List<String>> headers) {
        if (name == null || headers == null) {
            return "";
        }
        List<String> values = headers.get(name.toLowerCase());
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    /**
     * 轻量 JSONPath 子集，与 AssertEvaluator 保持一致。
     */
    private String extractByJsonPath(String path, String body) {
        if (path == null || body == null || !path.startsWith("$")) {
            return "";
        }
        try {
            Object current = com.apiweb.util.JsonUtils.fromJson(body, Object.class);
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
}
