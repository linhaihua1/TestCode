package com.apiweb.engine;

/**
 * 报告整体状态（取代 "passed" / "failed" / "error" 字面量）。
 *
 * <p>对应 {@code t_report.status}。明细行状态使用 {@link RunStatus}。
 */
public enum ReportStatus {

    /** 执行中 */
    RUNNING("running"),
    /** 全部通过 */
    PASSED("passed"),
    /** 存在断言失败 */
    FAILED("failed"),
    /** 存在执行异常 */
    ERROR("error");

    private final String code;

    ReportStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 由用例通过 / 失败 / 异常数推导报告状态。
     */
    public static ReportStatus resolve(int error, int failed, int total) {
        if (total == 0) return PASSED;
        if (error > 0) return ERROR;
        if (failed > 0) return FAILED;
        return PASSED;
    }
}
