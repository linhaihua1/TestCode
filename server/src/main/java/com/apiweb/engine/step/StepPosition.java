package com.apiweb.engine.step;

/**
 * 步骤位置（开发文档 §2.1 CaseStep.position）。
 *
 * <ul>
 *   <li>PRE：前置步骤（用例执行前初始化）</li>
 *   <li>TEST：测试步骤（用例主体）</li>
 *   <li>POST：后置步骤（用例执行后清理,失败也默认执行）</li>
 * </ul>
 */
public enum StepPosition {
    /** PRE：前置步骤，用例主体执行前运行（如登录、初始化数据） */
    PRE,
    /** TEST：测试步骤，用例主体 */
    TEST,
    /** POST：后置步骤，用例主体后运行（如清理），失败也默认执行 */
    POST;

    /**
     * 解析步骤位置。
     *
     * @param s 位置字符串（pre/test/post，大小写不敏感）
     * @return 对应位置；为空或无法识别时默认 {@code TEST}（绝大多数步骤在主体内）
     */
    public static StepPosition parse(String s) {
        if (s == null || s.isBlank()) return TEST;
        try {
            return StepPosition.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TEST;
        }
    }
}