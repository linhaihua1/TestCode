package com.apiweb.engine.step;

/**
 * 提取类型（需求文档 §4.2.3 5 种提取）。
 *
 * <ul>
 *   <li>HEADER：从响应头提取</li>
 *   <li>COOKIE：从 Set-Cookie 提取</li>
 *   <li>JSONPATH：从 JSON 响应体提取</li>
 *   <li>REGEX：从响应体正则匹配</li>
 *   <li>XPATH：从 XML/HTML 响应体提取</li>
 * </ul>
 */
public enum ExtractType {
    /** HEADER：按响应头名提取（不区分大小写） */
    HEADER,
    /** COOKIE：从 Set-Cookie 头中按 cookie 名提取值 */
    COOKIE,
    /** JSONPATH：用 JSONPath 从 JSON 响应体取值 */
    JSONPATH,
    /** REGEX：正则匹配响应体（有捕获组取 group(1)） */
    REGEX,
    /** XPATH：从 XML/HTML 响应体按 XPath 取值 */
    XPATH;

    /** 未指定 / 无法识别时的默认提取方式 */
    public static final ExtractType DEFAULT = JSONPATH;

    /**
     * 大小写不敏感解析（前端传 jsonpath / JSONPATH 均可）。
     *
     * <p>无法识别时回退到 {@link #DEFAULT}，不再列举字面量，避免与枚举名重复维护。
     */
    public static ExtractType parse(String s) {
        if (s == null || s.isBlank()) return DEFAULT;
        try {
            return ExtractType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }
}