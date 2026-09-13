package com.apiweb.report;

/**
 * 报告分享链接的访问结果（取代散落的 "success" / "not_found" / "expired" 等字面量）。
 *
 * <p>落库字段：{@code t_report_share_log.status}，用于分享访问审计。
 */
public enum ShareAccessStatus {

    /** 校验通过 */
    SUCCESS("success"),
    /** 分享链接不存在 */
    NOT_FOUND("not_found"),
    /** 已被创建者撤销 */
    REVOKED("revoked"),
    /** 超过有效期 */
    EXPIRED("expired"),
    /** 访问次数已达上限 */
    RATE_LIMITED("rate_limited"),
    /** 访问密码错误 */
    WRONG_PASSWORD("wrong_password"),
    /** 来源 IP 不在白名单 */
    IP_BLOCKED("ip_blocked");

    private final String code;

    ShareAccessStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
