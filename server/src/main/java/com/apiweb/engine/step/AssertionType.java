package com.apiweb.engine.step;

/**
 * 断言类型（需求文档 §4.2.3 8+ 种断言）。
 *
 * <ul>
 *   <li>STATUS：状态码（支持等于/范围）</li>
 *   <li>BODY_CONTAINS：响应体包含/不包含</li>
 *   <li>JSONPATH：JSONPath 字段值</li>
 *   <li>REGEX：正则匹配</li>
 *   <li>XPATH：XPath 节点值</li>
 *   <li>NUMERIC：数值比较（gt/lt/gte/lte/range）</li>
 *   <li>EMPTY：空值断言</li>
 *   <li>RESPONSE_TIME：响应时间阈值</li>
 *   <li>SCRIPT：脚本断言</li>
 *   <li>HEADER：响应头</li>
 * </ul>
 */
public enum AssertionType {
    STATUS, BODY_CONTAINS, JSONPATH, REGEX, XPATH,
    NUMERIC, EMPTY, RESPONSE_TIME, SCRIPT, HEADER;

    public static AssertionType parse(String s) {
        if (s == null || s.isBlank()) return BODY_CONTAINS;
        try {
            return AssertionType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        return switch (s.toLowerCase()) {
            case "body" -> BODY_CONTAINS;
            case "header" -> HEADER;
            case "jsonpath" -> JSONPATH;
            case "status" -> STATUS;
            case "responsetime", "response_time" -> RESPONSE_TIME;
            case "regex" -> REGEX;
            case "xpath" -> XPATH;
            default -> BODY_CONTAINS;
        };
    }
}