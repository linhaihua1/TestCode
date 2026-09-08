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
    HEADER, COOKIE, JSONPATH, REGEX, XPATH;

    public static ExtractType parse(String s) {
        if (s == null || s.isBlank()) return JSONPATH;
        try {
            return ExtractType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        return switch (s.toLowerCase()) {
            case "header" -> HEADER;
            case "cookie" -> COOKIE;
            case "jsonpath" -> JSONPATH;
            case "regex" -> REGEX;
            case "xpath" -> XPATH;
            default -> JSONPATH;
        };
    }
}