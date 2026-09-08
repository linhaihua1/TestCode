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
    PRE, TEST, POST;

    public static StepPosition parse(String s) {
        if (s == null || s.isBlank()) return TEST;
        try {
            return StepPosition.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TEST;
        }
    }
}