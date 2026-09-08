package com.apiweb.engine;

import com.apiweb.engine.step.ExtractType;
import com.apiweb.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量提取器（按需求文档 §4.2.3 5 种提取完整实现）。
 *
 * <ul>
 *   <li>{@code HEADER}：按 HTTP Header 名取值（不区分大小写）</li>
 *   <li>{@code COOKIE}：从 Set-Cookie 提取 cookie 名/值</li>
 *   <li>{@code JSONPATH}：JSONPath 路径</li>
 *   <li>{@code REGEX}：正则表达式</li>
 *   <li>{@code XPATH}：XPath 节点值（XML/HTML 响应）</li>
 * </ul>
 *
 * <p>每种提取可配置：
 * <ul>
 *   <li>默认值（{@code defaultValue}）：未命中时使用</li>
 *   <li>失败策略（{@code failStrategy}）：ignore / stop（stop 时未命中抛异常）</li>
 * </ul>
 */
@Slf4j
@Component
public class Extractor {

    public record Response(int status, Map<String, List<String>> headers, String body) {}

    /**
     * 扩展的提取定义（除原有字段外,新增 defaultValue / failStrategy）。
     * 兼容旧字段 {@code type}。
     */
    public record ExtractDef(String type, String expression, String variable,
                              String defaultValue, String failStrategy) {
        public static ExtractDef of(EngineDtos.Extract e) {
            return new ExtractDef(e.getType() != null ? e.getType() : e.getExpression(),
                    e.getExpression(), e.getVariable(), e.getDefaultValue(), e.getFailStrategy());
        }
    }

    public EngineDtos.ExtractResult extract(EngineDtos.Extract e, Response response) {
        return extract(ExtractDef.of(e), response);
    }

    public EngineDtos.ExtractResult extract(ExtractDef e, Response response) {
        ExtractType type = ExtractType.parse(e.type());
        String value;
        try {
            value = switch (type) {
                case HEADER -> extractHeader(e.expression(), response.headers());
                case COOKIE -> extractCookie(e.expression(), response.headers());
                case REGEX -> extractByRegex(e.expression(), response.body());
                case XPATH -> extractByXPath(e.expression(), response.body());
                case JSONPATH -> extractByJsonPath(e.expression(), response.body());
            };
        } catch (Exception ex) {
            value = "";
            log.warn("提取失败 type={} expr={}: {}", type, e.expression(), ex.getMessage());
        }
        if (value == null || value.isBlank()) {
            value = e.defaultValue() == null ? "" : e.defaultValue();
            if (value.isBlank() && "stop".equalsIgnoreCase(e.failStrategy())) {
                throw new IllegalStateException("提取失败 [type=" + type + ",expr=" + e.expression() + "]");
            }
        }
        return EngineDtos.ExtractResult.builder()
                .variable(e.variable())
                .value(value == null ? "" : value)
                .build();
    }

    /** 按 Header 名取值（Headers Map 的 key 应为小写） */
    private String extractHeader(String name, Map<String, List<String>> headers) {
        if (name == null || headers == null) return "";
        List<String> values = headers.get(name.toLowerCase());
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    /**
     * 从 Set-Cookie 中按 cookie 名取值。
     * 解析所有 Set-Cookie 头,匹配 {@code name=value} 段。
     */
    private String extractCookie(String name, Map<String, List<String>> headers) {
        if (name == null || headers == null) return "";
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!"set-cookie".equalsIgnoreCase(entry.getKey())) continue;
            for (String headerValue : entry.getValue()) {
                // Set-Cookie: SID=xxx; Path=/; HttpOnly
                for (String segment : headerValue.split(";")) {
                    String trimmed = segment.trim();
                    int eq = trimmed.indexOf('=');
                    if (eq > 0 && trimmed.substring(0, eq).equals(name)) {
                        return trimmed.substring(eq + 1);
                    }
                }
            }
        }
        return "";
    }

    /**
     * 正则提取：
     * <ul>
     *   <li>表达式含捕获组：返回 group(1)（业务常用）</li>
     *   <li>无捕获组：返回整个 match</li>
     *   <li>未命中：返回 ""</li>
     * </ul>
     */
    private String extractByRegex(String expression, String body) {
        if (expression == null || body == null) return "";
        Matcher matcher = Pattern.compile(expression, Pattern.DOTALL).matcher(body);
        if (matcher.find()) {
            return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
        }
        return "";
    }

    /**
     * XPath 提取：返回节点文本内容。
     */
    private String extractByXPath(String expression, String body) {
        if (expression == null || body == null || body.isBlank()) return "";
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new InputSource(new StringReader(body)));
            XPath xp = XPathFactory.newInstance().newXPath();
            Object result = xp.evaluate(expression, doc, XPathConstants.STRING);
            return result == null ? "" : String.valueOf(result);
        } catch (Exception e) {
            log.debug("XPath 提取失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 轻量 JSONPath 子集，与 {@link AssertEvaluator} 中的实现保持一致。
     */
    @SuppressWarnings("unchecked")
    private String extractByJsonPath(String path, String body) {
        if (path == null || body == null || !path.startsWith("$")) return "";
        try {
            Object current = JsonUtils.fromJson(body, Object.class);
            String[] segments = path.substring(1).split("\\.");
            for (String raw : segments) {
                if (raw.isEmpty()) continue;
                String segment = raw;
                int index = -1;
                int bracket = segment.indexOf('[');
                if (bracket >= 0 && segment.endsWith("]")) {
                    index = Integer.parseInt(segment.substring(bracket + 1, segment.length() - 1));
                    segment = segment.substring(0, bracket);
                }
                if (!segment.isEmpty()) {
                    if (current instanceof Map<?, ?> map) current = map.get(segment);
                    else return "";
                }
                if (index >= 0) {
                    if (current instanceof List<?> list && index < list.size()) current = list.get(index);
                    else return "";
                }
            }
            return current == null ? "" : String.valueOf(current);
        } catch (Exception e) {
            return "";
        }
    }
}