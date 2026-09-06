package com.apiweb.engine;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量提取器。
 *
 * <p>从一次 HTTP 响应中按规则提取字段，保存为变量供后续用例 / 步骤引用。
 *
 * <h3>三种提取方式</h3>
 * <ul>
 *   <li>{@code jsonpath}：JSONPath 路径（如 {@code $.data.userId}）</li>
 *   <li>{@code regex}：正则表达式（{@link Pattern#find()} 行为，捕获组 1 优先）</li>
 *   <li>{@code header}：按 HTTP Header 名取值（不区分大小写）</li>
 * </ul>
 *
 * <h3>典型场景</h3>
 * 登录接口响应 → 提取 token → 后续请求的 Authorization Header 用 {{token}} 引用。
 */
@Component
public class Extractor {

    /**
     * 响应数据：状态码、Headers、Body。
     * <p>为了轻量化，没有引入 javax.servlet 的 HttpServletResponse。</p>
     */
    public record Response(int status, Map<String, List<String>> headers, String body) {}

    /**
     * 执行一次提取。
     *
     * @param e        提取规则
     * @param response 当前步骤的响应
     * @return 提取结果（即使未命中也返回，value=""）
     */
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

    /**
     * 正则提取：
     * <ul>
     *   <li>表达式含捕获组：返回 group(1)（业务常用）</li>
     *   <li>无捕获组：返回整个 match（如 "userId":123 整段）</li>
     *   <li>未命中：返回 ""</li>
     * </ul>
     */
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

    /**
     * 按 Header 名取值（Headers Map 的 key 应为小写）。
     */
    private String extractHeader(String name, Map<String, List<String>> headers) {
        if (name == null || headers == null) {
            return "";
        }
        List<String> values = headers.get(name.toLowerCase());
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    /**
     * 轻量 JSONPath 子集，与 {@link AssertEvaluator} 中的实现保持一致。
     *
     * <p>支持的语法：{@code $.a.b} / {@code $.a[0]} / {@code $.a[0].b}。
     * 不支持过滤、通配符、函数等高级特性 —— 如有需求可引入 {@code com.jayway.jsonpath}。
     *
     * @return 命中值（toString），未命中返回 ""
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
