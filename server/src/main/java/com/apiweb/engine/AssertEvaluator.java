package com.apiweb.engine;

import com.apiweb.engine.step.AssertionType;
import com.apiweb.engine.step.ExtractType;
import com.apiweb.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 断言评估器（按 §4.2.3 9 种断言完整实现）。
 *
 * <p>每条断言通过 {@code type} 字段路由到对应实现；旧版本 {@code source} 字段（status/header/body/jsonpath/responsetime）
 * 自动映射到新枚举。
 *
 * <h3>9 种断言类型</h3>
 * <ol>
 *   <li>{@code STATUS}：状态码等于/范围（如 200-299）</li>
 *   <li>{@code HEADER}：响应头字段值</li>
 *   <li>{@code BODY_CONTAINS}：响应体包含/不包含</li>
 *   <li>{@code JSONPATH}：JSON 字段值</li>
 *   <li>{@code XPATH}：XML/HTML 节点值（新增）</li>
 *   <li>{@code REGEX}：正则匹配</li>
 *   <li>{@code NUMERIC}：数值比较（gt/lt/gte/lte/range）</li>
 *   <li>{@code EMPTY}：判空/非空</li>
 *   <li>{@code RESPONSE_TIME}：响应时间阈值</li>
 *   <li>{@code SCRIPT}：脚本断言（新增,JS）</li>
 * </ol>
 */
@Slf4j
@Component
public class AssertEvaluator {

    /**
     * 单条断言的输入数据：响应状态码、Headers、Body、响应耗时。
     */
    public record EvalInput(int status, Map<String, List<String>> headers, String body, long responseTimeMs) {}

    /**
     * 评估单条断言。
     *
     * @param a     断言定义（兼容 source/type 两种字段）
     * @param input 响应数据
     * @return 评估结果
     */
    public EngineDtos.AssertionResult evaluate(EngineDtos.Assertion a, EvalInput input) {
        AssertionType type = AssertionType.parse(a.getSource() != null ? a.getSource() : a.getType());
        try {
            return switch (type) {
                case STATUS -> assertStatus(a, input);
                case HEADER -> assertHeader(a, input);
                case BODY_CONTAINS -> assertBodyContains(a, input);
                case JSONPATH -> assertJsonPath(a, input);
                case XPATH -> assertXPath(a, input);
                case REGEX -> assertRegex(a, input);
                case NUMERIC -> assertNumeric(a, input);
                case EMPTY -> assertEmpty(a, input);
                case RESPONSE_TIME -> assertResponseTime(a, input);
                case SCRIPT -> assertScript(a, input);
            };
        } catch (Exception e) {
            return EngineDtos.AssertionResult.builder()
                    .source(type.name())
                    .operator(a.getOperator())
                    .expected(a.getExpected())
                    .passed(false)
                    .message("断言执行异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 批量评估多条断言。
     *
     * <p>某条断言抛异常会被捕获，对应结果标记为失败，不影响其它断言评估。
     */
    public List<EngineDtos.AssertionResult> evaluateAll(List<EngineDtos.Assertion> assertions, EvalInput input) {
        List<EngineDtos.AssertionResult> results = new ArrayList<>();
        if (assertions == null) return results;
        for (EngineDtos.Assertion a : assertions) {
            results.add(evaluate(a, input));
        }
        return results;
    }

    // ============================================================
    // 各种断言的具体实现
    // ============================================================

    private EngineDtos.AssertionResult assertStatus(EngineDtos.Assertion a, EvalInput input) {
        // operator: equals(=X) / not_equals(!=X) / in_range(200-299)
        String op = nullSafe(a.getOperator(), "equals").toLowerCase();
        String actual = String.valueOf(input.status());
        boolean passed = switch (op) {
            case "in_range", "range" -> {
                String[] parts = a.getExpected().split("-");
                if (parts.length != 2) yield false;
                int lo = Integer.parseInt(parts[0].trim());
                int hi = Integer.parseInt(parts[1].trim());
                yield input.status() >= lo && input.status() <= hi;
            }
            case "not_equals", "!=" -> input.status() != Integer.parseInt(a.getExpected());
            default -> input.status() == Integer.parseInt(a.getExpected());
        };
        return buildResult("STATUS", null, op, a.getExpected(), actual, passed,
                "[status] " + op + " '" + a.getExpected() + "'");
    }

    private EngineDtos.AssertionResult assertHeader(EngineDtos.Assertion a, EvalInput input) {
        String headerName = a.getProperty() == null ? "" : a.getProperty().toLowerCase();
        List<String> values = input.headers() == null ? null : input.headers().get(headerName);
        String actual = (values == null || values.isEmpty()) ? "" : values.get(0);
        boolean passed = applyStringOp(nullSafe(a.getOperator(), "equals"), a.getExpected(), actual);
        return buildResult("HEADER", a.getProperty(), a.getOperator(), a.getExpected(),
                truncate(actual), passed, "[header:" + a.getProperty() + "] " + a.getOperator());
    }

    private EngineDtos.AssertionResult assertBodyContains(EngineDtos.Assertion a, EvalInput input) {
        String body = input.body() == null ? "" : input.body();
        boolean passed = body.contains(a.getExpected() == null ? "" : a.getExpected());
        if ("not_contains".equalsIgnoreCase(a.getOperator())) passed = !passed;
        return buildResult("BODY_CONTAINS", null, a.getOperator(), a.getExpected(),
                truncate(body), passed, "[body] contains '" + a.getExpected() + "'");
    }

    private EngineDtos.AssertionResult assertJsonPath(EngineDtos.Assertion a, EvalInput input) {
        String actual = jsonPath(input.body(), a.getProperty());
        boolean passed = applyStringOp(nullSafe(a.getOperator(), "equals"), a.getExpected(), actual);
        return buildResult("JSONPATH", a.getProperty(), a.getOperator(), a.getExpected(),
                truncate(actual), passed, "[jsonpath:" + a.getProperty() + "]");
    }

    private EngineDtos.AssertionResult assertXPath(EngineDtos.Assertion a, EvalInput input) {
        String actual = xPath(input.body(), a.getProperty());
        boolean passed = applyStringOp(nullSafe(a.getOperator(), "equals"), a.getExpected(), actual);
        return buildResult("XPATH", a.getProperty(), a.getOperator(), a.getExpected(),
                truncate(actual), passed, "[xpath:" + a.getProperty() + "]");
    }

    private EngineDtos.AssertionResult assertRegex(EngineDtos.Assertion a, EvalInput input) {
        String body = input.body() == null ? "" : input.body();
        boolean passed;
        try {
            passed = Pattern.compile(a.getExpected()).matcher(body).find();
        } catch (Exception e) {
            passed = false;
        }
        if ("not_match".equalsIgnoreCase(a.getOperator())) passed = !passed;
        return buildResult("REGEX", null, a.getOperator(), a.getExpected(),
                truncate(body), passed, "[regex] '" + a.getExpected() + "'");
    }

    private EngineDtos.AssertionResult assertNumeric(EngineDtos.Assertion a, EvalInput input) {
        String actualStr = input.body() == null ? "0" : input.body();
        String expectedStr = a.getExpected() == null ? "0" : a.getExpected();
        // 如果 property 是 JSONPath 表达式,先取值再比较
        if (a.getProperty() != null && a.getProperty().startsWith("$")) {
            actualStr = jsonPath(input.body(), a.getProperty());
        }
        double actual = toNumber(actualStr);
        double expected = toNumber(expectedStr);
        boolean passed = switch (nullSafe(a.getOperator(), "equals").toLowerCase()) {
            case "gt", ">" -> actual > expected;
            case "lt", "<" -> actual < expected;
            case "gte", ">=" -> actual >= expected;
            case "lte", "<=" -> actual <= expected;
            case "range" -> {
                String[] parts = expectedStr.split("-");
                if (parts.length != 2) yield false;
                yield actual >= toNumber(parts[0]) && actual <= toNumber(parts[1]);
            }
            default -> actual == expected;
        };
        return buildResult("NUMERIC", a.getProperty(), a.getOperator(), expectedStr,
                String.valueOf(actual), passed, "[numeric]");
    }

    private EngineDtos.AssertionResult assertEmpty(EngineDtos.Assertion a, EvalInput input) {
        String actual = input.body() == null ? "" : input.body();
        if (a.getProperty() != null && a.getProperty().startsWith("$")) {
            actual = jsonPath(input.body(), a.getProperty());
        }
        boolean isEmpty = actual == null || actual.isBlank();
        boolean passed = "empty".equalsIgnoreCase(a.getOperator()) ? isEmpty : !isEmpty;
        return buildResult("EMPTY", a.getProperty(), a.getOperator(), null,
                truncate(actual), passed, "[" + a.getOperator() + "]");
    }

    private EngineDtos.AssertionResult assertResponseTime(EngineDtos.Assertion a, EvalInput input) {
        boolean passed = switch (nullSafe(a.getOperator(), "lte").toLowerCase()) {
            case "lte", "<=" -> input.responseTimeMs() <= toNumber(a.getExpected());
            case "gte", ">=" -> input.responseTimeMs() >= toNumber(a.getExpected());
            case "lt", "<" -> input.responseTimeMs() < toNumber(a.getExpected());
            case "gt", ">" -> input.responseTimeMs() > toNumber(a.getExpected());
            default -> false;
        };
        return buildResult("RESPONSE_TIME", null, a.getOperator(), a.getExpected(),
                String.valueOf(input.responseTimeMs()), passed, "[response_time]");
    }

    /**
     * 脚本断言（JS）：执行一段脚本,返回 true 视为通过。
     * 上下文中可用变量：status / body / headers / responseTime / expected
     * （便于用户写 status === 200 或 body.includes('xxx')）
     */
    private EngineDtos.AssertionResult assertScript(EngineDtos.Assertion a, EvalInput input) {
        String script = a.getExpected();
        if (script == null || script.isBlank()) {
            return buildResult("SCRIPT", null, "script", null, "", false, "脚本为空");
        }
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
            if (engine == null) {
                return buildResult("SCRIPT", null, "script", null, "", false,
                        "当前 JVM 无 JS 引擎（建议引入 org.graalvm.polyglot:js）");
            }
            engine.put("status", input.status());
            engine.put("body", input.body() == null ? "" : input.body());
            engine.put("responseTime", input.responseTimeMs());
            engine.put("headers", input.headers());
            Object ret = engine.eval(script);
            boolean passed = Boolean.TRUE.equals(ret);
            return buildResult("SCRIPT", null, "script", truncate(script), String.valueOf(ret),
                    passed, "[script] " + truncate(script));
        } catch (Exception e) {
            return buildResult("SCRIPT", null, "script", null, "", false,
                    "脚本执行失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private boolean applyStringOp(String op, String expected, String actual) {
        return switch (op.toLowerCase()) {
            case "not_equals", "!=" -> !safeEquals(expected, actual);
            case "contains" -> actual != null && actual.contains(expected == null ? "" : expected);
            case "not_contains" -> actual == null || !actual.contains(expected == null ? "" : expected);
            case "regex" -> actual != null && Pattern.compile(expected == null ? "" : expected)
                    .matcher(actual).find();
            case "empty" -> actual == null || actual.isBlank();
            case "not_empty" -> actual != null && !actual.isBlank();
            default -> safeEquals(expected, actual);
        };
    }

    private EngineDtos.AssertionResult buildResult(String source, String property, String operator,
                                                   String expected, String actual, boolean passed,
                                                   String desc) {
        String msg = passed ? "通过" : "断言失败: " + desc + ", 实际值: " + truncate(actual);
        return EngineDtos.AssertionResult.builder()
                .source(source)
                .property(property)
                .operator(operator)
                .expected(expected)
                .actual(truncate(actual))
                .passed(passed)
                .message(msg)
                .build();
    }

    /**
     * 轻量级 JSONPath 实现（支持 $.a.b / $.a[0].b）。
     */
    @SuppressWarnings("unchecked")
    private String jsonPath(String body, String path) {
        if (body == null || path == null || !path.startsWith("$")) return "";
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

    /**
     * XPath 解析（XML/HTML 响应）。
     *
     * <p>使用 JDK 自带 {@link javax.xml.xpath.XPath},无需额外依赖。
     * HTML 不规范时可能抛 SAXException,会被捕获返回 ""。
     */
    private String xPath(String body, String expression) {
        if (body == null || expression == null || expression.isBlank()) return "";
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // 禁用 DTD 与外部实体,防止 XXE 攻击
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new InputSource(new StringReader(body)));
            XPath xpath = XPathFactory.newInstance().newXPath();
            Object result = xpath.evaluate(expression, doc, XPathConstants.STRING);
            if (result instanceof String s) return s;
            if (result instanceof NodeList nodes) {
                return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
            }
            return result == null ? "" : String.valueOf(result);
        } catch (Exception e) {
            log.debug("XPath 解析失败: {}", e.getMessage());
            return "";
        }
    }

    private static String nullSafe(String s, String def) {
        return s == null || s.isBlank() ? def : s;
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

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() > 500 ? value.substring(0, 500) + "...(截断)" : value;
    }
}