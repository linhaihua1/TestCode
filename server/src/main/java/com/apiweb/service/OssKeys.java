package com.apiweb.service;

/**
 * 对象存储 key 命名规则（集中管理，避免各调用点硬编码前缀字符串）。
 *
 * <p>key 结构：{@code {命名空间}/{业务 ID}/{文件名}}，便于按业务清理与排查。
 */
public final class OssKeys {

    private OssKeys() {
    }

    /** 接口测试报告明细 */
    private static final String REPORTS = "reports/";
    /** 调试记录 */
    private static final String DEBUG_RECORDS = "debug-records/";
    /** UI 测试报告明细 */
    private static final String UI_REPORTS = "ui-reports/";
    /** 性能测试报告数据 */
    private static final String PERF_REPORTS = "perf-reports/";

    /** 明细文件名 */
    private static final String DETAILS_FILE = "details.json";
    /** 步骤文件名 */
    private static final String STEPS_FILE = "steps.json";

    /**
     * 接口报告明细 key：reports/{runId}/details.json
     *
     * @param runId 测试运行 ID
     * @return 对象存储 key
     */
    public static String reportDetails(String runId) {
        return REPORTS + runId + "/" + DETAILS_FILE;
    }

    /**
     * UI 报告明细 key：ui-reports/{runId}/details.json
     *
     * @param runId UI 运行 ID
     * @return 对象存储 key
     */
    public static String uiReportDetails(String runId) {
        return UI_REPORTS + runId + "/" + DETAILS_FILE;
    }

    /**
     * 性能报告数据 key：perf-reports/{runId}/{fileName}
     *
     * @param runId   性能运行 ID
     * @param fileName 数据文件名（如 summary.json / series.json）
     * @return 对象存储 key
     */
    public static String perfReportData(String runId, String fileName) {
        return PERF_REPORTS + runId + "/" + fileName;
    }

    /**
     * 调试记录步骤 key：debug-records/{recordId}/{时间戳}/steps.json
     *
     * @param recordId 调试记录 ID
     * @param timestamp 时间戳（毫秒），用于区分同一记录的多次调试
     * @return 对象存储 key
     */
    public static String debugRecordSteps(String recordId, long timestamp) {
        return DEBUG_RECORDS + recordId + "/" + timestamp + "/" + STEPS_FILE;
    }
}
