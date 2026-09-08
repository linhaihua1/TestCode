package com.apiweb.engine.step;

/**
 * 用例步骤类型枚举（对标 MeterSphere 步骤 + 开发文档 §2.1 CaseStep.stepType）。
 *
 * <h3>10 种类型</h3>
 * <table>
 *   <tr><th>枚举</th><th>名称</th><th>用途</th></tr>
 *   <tr><td>HTTP_REQUEST</td><td>接口请求</td><td>单次 HTTP 调用 + 断言 + 提取</td></tr>
 *   <tr><td>SCRIPT</td><td>自定义脚本</td><td>JS/Python 脚本执行,可读写变量</td></tr>
 *   <tr><td>WAIT</td><td>等待</td><td>固定等待/动态条件等待</td></tr>
 *   <tr><td>VARIABLE_ASSIGN</td><td>变量赋值</td><td>直接对变量赋值或表达式计算</td></tr>
 *   <tr><td>IF</td><td>条件分支</td><td>IF-ELSE 二选一执行 children</td></tr>
 *   <tr><td>FOR</td><td>计数循环</td><td>FOR 循环遍历 list/times</td></tr>
 *   <tr><td>WHILE</td><td>条件循环</td><td>满足条件时重复执行</td></tr>
 *   <tr><td>TRANSACTION</td><td>事务控制器</td><td>整体成功/失败判定规则</td></tr>
 *   <tr><td>ONCE</td><td>仅一次控制器</td><td>整个任务执行期间只跑一次</td></tr>
 *   <tr><td>REF_PUBLIC_CASE</td><td>引用公共用例</td><td>嵌套执行公共用例</td></tr>
 * </table>
 *
 * <h3>类型识别</h3>
 * 前端传来的步骤 JSON 里 {@code type} 字段必须为枚举名（HTTP_REQUEST / SCRIPT 等），
 * 旧版本兼容 {@code http / if / for / while / script} 写法。
 */
public enum StepType {
    HTTP_REQUEST,
    SCRIPT,
    WAIT,
    VARIABLE_ASSIGN,
    IF,
    FOR,
    WHILE,
    TRANSACTION,
    ONCE,
    REF_PUBLIC_CASE;

    /**
     * 兼容解析：旧版本的简短字符串也能识别。
     *
     * @param s type 字段
     * @return 找不到时默认 HTTP_REQUEST
     */
    public static StepType parse(String s) {
        if (s == null || s.isBlank()) return HTTP_REQUEST;
        try {
            return StepType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        return switch (s.toLowerCase()) {
            case "http" -> HTTP_REQUEST;
            case "if" -> IF;
            case "for" -> FOR;
            case "while" -> WHILE;
            case "script" -> SCRIPT;
            case "wait" -> WAIT;
            case "variable_assign", "variableAssign" -> VARIABLE_ASSIGN;
            case "transaction" -> TRANSACTION;
            case "once" -> ONCE;
            case "ref_public_case", "refPublicCase" -> REF_PUBLIC_CASE;
            default -> HTTP_REQUEST;
        };
    }

    /** 是否为容器（带 children 的控制器） */
    public boolean isContainer() {
        return this == IF || this == FOR || this == WHILE
                || this == TRANSACTION || this == ONCE || this == REF_PUBLIC_CASE;
    }
}