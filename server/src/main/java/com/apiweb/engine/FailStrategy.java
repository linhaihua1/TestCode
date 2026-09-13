package com.apiweb.engine;

/**
 * 任务失败策略（取代 "stop_on_fail" / "continue_all" / "retry_then_stop" 字面量）。
 *
 * <p>对应 {@code t_test_task.fail_strategy}，取值与前端下拉项保持一致。
 */
public enum FailStrategy {

    /** 任一用例失败即停止后续用例 */
    STOP_ON_FAIL("stop_on_fail"),
    /** 全部用例跑完，失败不中断 */
    CONTINUE_ALL("continue_all"),
    /** 失败后按 retryCount 重试，全部失败后停止 */
    RETRY_THEN_STOP("retry_then_stop");

    /** 默认策略 */
    public static final FailStrategy DEFAULT = STOP_ON_FAIL;

    private final String code;

    FailStrategy(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 解析，无法识别时返回默认值 */
    public static FailStrategy parseOrDefault(String s) {
        if (s != null) {
            for (FailStrategy v : values()) {
                if (v.code.equalsIgnoreCase(s)) return v;
            }
        }
        return DEFAULT;
    }

    /** 该策略下是否需要在失败时停止后续用例 */
    public boolean stopOnFailure() {
        return this == STOP_ON_FAIL || this == RETRY_THEN_STOP;
    }
}
