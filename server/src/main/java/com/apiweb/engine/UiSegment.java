package com.apiweb.engine;

/**
 * UI 用例步骤分段（取代 "setup" / "test" / "teardown" 字面量）。
 *
 * <p>UI 用例由三段步骤组成，执行结果明细中 {@code segment} 字段取本枚举编码。
 */
public enum UiSegment {

    /** 前置步骤 */
    SETUP("setup"),
    /** 测试步骤 */
    TEST("test"),
    /** 后置步骤 */
    TEARDOWN("teardown"),
    /** 分段执行整体异常（非某一段） */
    ERROR("error");

    private final String code;

    UiSegment(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
