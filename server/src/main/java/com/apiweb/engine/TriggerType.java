package com.apiweb.engine;

/**
 * 报告 / 任务触发方式（取代 "manual" / "schedule" / "webhook" / "api" 字面量）。
 *
 * <p>对应 {@code t_report.trigger_type}。
 */
public enum TriggerType {

    /** 页面手动触发 */
    MANUAL("manual"),
    /** 定时任务触发 */
    SCHEDULE("schedule"),
    /** CI/CD webhook 触发 */
    WEBHOOK("webhook"),
    /** 开放 API 触发 */
    API("api"),
    /** Jenkins 流水线触发（平台调用 Jenkins Job，或 Jenkins 回调平台） */
    JENKINS("jenkins");

    private final String code;

    TriggerType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 解析，无法识别返回 null */
    public static TriggerType parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (TriggerType v : values()) {
            if (v.code.equalsIgnoreCase(s)) return v;
        }
        return null;
    }
}
