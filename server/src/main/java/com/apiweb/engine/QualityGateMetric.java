package com.apiweb.engine;

import java.util.Locale;

/**
 * 质量门禁指标（取代门禁规则里散落的 "fail_rate" 等字面量）。
 *
 * <p>门禁规则语义：<b>指标值 {@code operator} 阈值 → 视为违规，流水线判定「未通过」</b>。
 * 例如「失败率 &gt; 5%」配成 {@code {metric: fail_rate, operator: gt, threshold: 5}}。
 *
 * <p>各指标从不同测试结果中取值：
 * <ul>
 *   <li>接口自动化：{@link #PASS_RATE} / {@link #FAIL_RATE} / {@link #ERROR_RATE} /
 *       {@link #AVG_RESPONSE_TIME} / {@link #P95_RESPONSE_TIME}</li>
 *   <li>UI 自动化：{@link #PASS_RATE} / {@link #FAIL_RATE} / {@link #ERROR_RATE}</li>
 *   <li>性能测试：{@link #ERROR_RATE} / {@link #AVG_RESPONSE_TIME} / {@link #P95_RESPONSE_TIME} /
 *       {@link #TPS} / {@link #ERROR_COUNT}</li>
 * </ul>
 */
public enum QualityGateMetric {

    /** 用例通过率（%） */
    PASS_RATE("pass_rate", "通过率", "%"),
    /** 用例失败率（%） */
    FAIL_RATE("fail_rate", "失败率", "%"),
    /** 错误率（接口 error / 性能 sampler 错误率，%） */
    ERROR_RATE("error_rate", "错误率", "%"),
    /** 平均响应时间（ms） */
    AVG_RESPONSE_TIME("avg_response_time", "平均响应时间", "ms"),
    /** P95 响应时间（ms） */
    P95_RESPONSE_TIME("p95_response_time", "P95 响应时间", "ms"),
    /** 错误样本数（性能测试） */
    ERROR_COUNT("error_count", "错误样本数", "个"),
    /** 吞吐量 TPS（性能测试） */
    TPS("tps", "吞吐量", "tps");

    private final String code;
    private final String label;
    private final String unit;

    QualityGateMetric(String code, String label, String unit) {
        this.code = code;
        this.label = label;
        this.unit = unit;
    }

    /** 前后端 / DB 契约值 */
    public String getCode() {
        return code;
    }

    /** 中文展示名 */
    public String getLabel() {
        return label;
    }

    /** 指标单位，用于前端展示 */
    public String getUnit() {
        return unit;
    }

    /** 中文展示名 + 单位，如「平均响应时间(ms)」 */
    public String getLabelWithUnit() {
        return label + "(" + unit + ")";
    }

    /** 大小写不敏感解析，无法识别返回 null */
    public static QualityGateMetric parse(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.toLowerCase(Locale.ROOT);
        for (QualityGateMetric m : values()) {
            if (m.code.equals(v)) return m;
        }
        return null;
    }
}
