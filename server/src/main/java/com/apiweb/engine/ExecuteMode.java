package com.apiweb.engine;

/**
 * 任务执行模式（取代 "sequential" / "parallel" 字面量）。
 *
 * <p>对应 {@code t_test_task.execute_mode}。
 */
public enum ExecuteMode {

    /** 顺序执行 */
    SEQUENTIAL("sequential"),
    /** 并行执行 */
    PARALLEL("parallel");

    /** 默认执行模式 */
    public static final ExecuteMode DEFAULT = SEQUENTIAL;

    private final String code;

    ExecuteMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 解析，无法识别时返回默认值 */
    public static ExecuteMode parseOrDefault(String s) {
        if (s != null) {
            for (ExecuteMode v : values()) {
                if (v.code.equalsIgnoreCase(s)) return v;
            }
        }
        return DEFAULT;
    }
}
