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
    /** STATUS：校验 HTTP 状态码（等于或范围，如 200-299） */
    STATUS,
    /** BODY_CONTAINS：响应体包含/不包含某子串 */
    BODY_CONTAINS,
    /** JSONPATH：用 JSONPath 取值后与期望值比较 */
    JSONPATH,
    /** REGEX：正则匹配响应体 */
    REGEX,
    /** XPATH：从 XML/HTML 响应体按 XPath 取值 */
    XPATH,
    /** NUMERIC：数值比较（gt/lt/gte/lte/range） */
    NUMERIC,
    /** EMPTY：判空/非空 */
    EMPTY,
    /** RESPONSE_TIME：响应耗时阈值 */
    RESPONSE_TIME,
    /** SCRIPT：执行 JS 脚本断言，返回 true 视为通过 */
    SCRIPT,
    /** HEADER：按响应头名取值后比较 */
    HEADER;

    /**
     * 大小写不敏感解析断言类型。
     *
     * <p>兼容旧版 {@code source} 取值（body/header/jsonpath/status/responsetime/regex 等）。
     * 无法识别或为空时回退到 {@code BODY_CONTAINS}（最常见的断言）。
     *
     * @param s 断言类型字符串
     * @return 对应的断言类型
     */
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