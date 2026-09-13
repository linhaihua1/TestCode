package com.apiweb.engine;

/**
 * 质量门禁比较操作符（取代 "&gt;" / "&lt;" 等符号字面量）。
 *
 * <p>语义：<b>指标值 操作符 阈值</b>成立即判定违规（门禁不通过）。
 * 因此「通过率低于 95% 不通过」要配成 {@code pass_rate lt 95}。
 */
public enum QualityGateOperator {

    /** 大于阈值 */
    GT("gt", ">"),
    /** 大于等于阈值 */
    GTE("gte", ">="),
    /** 小于阈值 */
    LT("lt", "<"),
    /** 小于等于阈值 */
    LTE("lte", "<="),
    /** 等于阈值 */
    EQ("eq", "=");

    private final String code;
    private final String symbol;

    QualityGateOperator(String code, String symbol) {
        this.code = code;
        this.symbol = symbol;
    }

    /** 前后端 / DB 契约值 */
    public String getCode() {
        return code;
    }

    /** 数学符号，用于前端展示 */
    public String getSymbol() {
        return symbol;
    }

    /**
     * 判定是否违规。
     *
     * @param actual    实际指标值
     * @param threshold 阈值
     * @return true=违反该条规则（门禁不通过）
     */
    public boolean violate(double actual, double threshold) {
        return switch (this) {
            case GT -> actual > threshold;
            case GTE -> actual >= threshold;
            case LT -> actual < threshold;
            case LTE -> actual <= threshold;
            case EQ -> Double.compare(actual, threshold) == 0;
        };
    }

    /** 大小写不敏感解析，无法识别返回 null */
    public static QualityGateOperator parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (QualityGateOperator v : values()) {
            if (v.code.equalsIgnoreCase(s)) return v;
        }
        return null;
    }

    /** 解析，无法识别返回 {@link #GT} */
    public static QualityGateOperator parseOrDefault(String s) {
        QualityGateOperator v = parse(s);
        return v == null ? GT : v;
    }
}
