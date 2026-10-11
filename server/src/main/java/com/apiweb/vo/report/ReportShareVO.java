package com.apiweb.vo.report;

import lombok.Data;

import java.time.Instant;

/**
 * 报告分享 VO（响应）。
 *
 * <p>与 {@code ReportShareEntity} 解耦：不含 password（BCrypt 哈希）、
 * allowedIps 等敏感字段，避免外泄。</p>
 */
@Data
public class ReportShareVO {

    /** 分享记录 ID */
    private String id;
    /** 分享 token（URL 中实际使用的字符串） */
    private String token;
    /** 所属报告 ID */
    private String reportId;
    /** 创建者 */
    private String createdBy;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 过期时间 */
    private Instant expiresAt;
    /** 是否已撤销 */
    private Boolean revoked;
    /** 累计访问次数 */
    private Integer accessCount;
    /** 最近一次访问时间 */
    private Instant lastAccessedAt;
    /** 最大访问次数上限 */
    private Integer maxAccessCount;
}
