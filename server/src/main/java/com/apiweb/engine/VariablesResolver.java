package com.apiweb.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量解析器。
 *
 * <p>在用例执行过程中，HTTP 请求的 URL / Headers / Body 中可能包含 {{varName}} 占位符，
 * 这些占位符需要在执行前替换为真实的运行时变量值。
 *
 * <h3>变量来源（按优先级叠加）</h3>
 * <ol>
 *   <li>提取变量：从前序步骤的响应中提取（如 {{userId}} = 12345）</li>
 *   <li>用例变量：用例定义中写死的变量</li>
 *   <li>环境变量：当前选中环境的 host/headers/variables</li>
 *   <li>全局变量：跨项目共享的变量（如第三方 API key）</li>
 * </ol>
 *
 * <h3>占位符语法</h3>
 * <ul>
 *   <li>{@code {{name}}}：单层变量</li>
 *   <li>{@code {{prefix.suffix}}}：支持 . 和 - 作为分隔符（JSONPath 风格）</li>
 *   <li>{@code ${response.body.data.token}}：访问前序响应（由 {@link Extractor} 转换）</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>
 *   VariablesResolver resolver = new VariablesResolver(new LinkedHashMap<>());
 *   resolver.put("baseUrl", "https://api.example.com");
 *   resolver.put("token", "eyJhbGci...");
 *   String url = resolver.resolve("{{baseUrl}}/users/{{userId}}");
 *   // 结果: "https://api.example.com/users/12345"
 * </pre>
 */
public class VariablesResolver {

    /**
     * 匹配 {{var}} 形式的占位符。
     * <p>变量名允许：字母、数字、下划线、点、中划线（点用于 JSONPath 风格）。</p>
     */
    private static final Pattern PATTERN = Pattern.compile("\\{\\{\\s*([\\w.\\-]+)\\s*}}");

    /** 运行时变量池（key -> value），按优先级顺序 put（后 put 覆盖前 put） */
    private final Map<String, String> variables;

    /**
     * @param variables 已按优先级填充好的变量池。调用方负责准备数据。
     */
    public VariablesResolver(Map<String, String> variables) {
        this.variables = variables;
    }

    /**
     * 设置 / 覆盖一个变量。
     *
     * @param key   变量名
     * @param value 值（null 会被存为空字符串）
     */
    public void put(String key, String value) {
        variables.put(key, value == null ? "" : value);
    }

    /**
     * 获取变量值（不存在时返回 null）。
     */
    public String get(String key) {
        return variables.get(key);
    }

    /**
     * 获取所有变量（用于报告展示"用例用到了哪些变量"）。
     */
    public Map<String, String> all() {
        return variables;
    }

    /**
     * 解析模板字符串中的所有 {{var}} 占位符。
     *
     * <p>规则：
     * <ul>
     *   <li>命中变量：用变量值替换</li>
     *   <li>未命中变量：保留原始 {{var}} 字面量（便于报告调试）</li>
     *   <li>替换值包含特殊字符（$、\）：自动转义，不会破坏正则</li>
     * </ul>
     *
     * @param template 含占位符的模板字符串（可为空）
     * @return 替换后的字符串；template 为 null 时返回 null
     */
    public String resolve(String template) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            // 变量不存在时保留原占位符（便于调试报告中看到"该变量没值"）
            String value = variables.getOrDefault(name, matcher.group(0));
            // quoteReplacement：替换值中的 $ 和 \ 会被视为正则特殊字符，需转义
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
