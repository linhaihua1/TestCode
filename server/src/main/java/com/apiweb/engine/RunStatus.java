package com.apiweb.engine;

/**
 * 执行状态枚举（取代散落的 "pending"/"running"/"success"/"failed"/"error"/"skipped" 字面量）。
 *
 * <p>同时用于：
 * <ul>
 *   <li>步骤结果 {@code StepResult.status}</li>
 *   <li>用例 / 任务运行记录 {@code t_test_task_run.result}</li>
 *   <li>报告整体状态 {@code t_report.status}</li>
 *   <li>UI / 性能报告状态 {@code t_ui_report.status} / {@code t_perf_report.status}</li>
 * </ul>
 */
public enum RunStatus {

    /** 待执行 */
    PENDING("pending"),
    /** 执行中 */
    RUNNING("running"),
    /** 成功 */
    SUCCESS("success"),
    /** 断言失败 */
    FAILED("failed"),
    /** 执行异常 */
    ERROR("error"),
    /** 跳过 / 未执行 */
    SKIPPED("skipped");

    private final String code;

    RunStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 大小写不敏感解析，无法识别返回 null */
    public static RunStatus parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (RunStatus v : values()) {
            if (v.code.equalsIgnoreCase(s)) return v;
        }
        return null;
    }

    /** 是否为「未通过」状态（失败或异常） */
    public static boolean isFailure(String s) {
        return FAILED.code.equals(s) || ERROR.code.equals(s);
    }
}
