package com.apiweb.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量解析器：将 {{var}} 占位符替换为运行时变量（环境变量 + 全局变量 + 提取变量）。
 * 优先级：提取变量（最近覆盖）> 环境变量 > 全局变量。
 */
public class VariablesResolver {

    private static final Pattern PATTERN = Pattern.compile("\\{\\{\\s*([\\w.\\-]+)\\s*}}");

    private final Map<String, String> variables;

    public VariablesResolver(Map<String, String> variables) {
        this.variables = variables;
    }

    public void put(String key, String value) {
        variables.put(key, value == null ? "" : value);
    }

    public String get(String key) {
        return variables.get(key);
    }

    public Map<String, String> all() {
        return variables;
    }

    /**
     * 递归解析字符串中的 {{var}} 占位符。
     */
    public String resolve(String template) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.getOrDefault(name, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
