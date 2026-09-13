package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * * 报告分享访问日志（审计用途）。
 *
 * <p>对应表：{@code t_report_share_log}。
 * 分享链接的每一次访问（成功或拒绝）都会留下日志，便于排查越权与泄露。</p>
 */
@Data
@TableName("t_report_share_log")
public class ReportShareLogEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属分享链接 ID */
    private String shareId;
    /** 所属报告 ID */
    private String reportId;
    /** 访问者 IP */
    private String accessIp;
    /** 访问者 User-Agent */
    private String userAgent;
    /** 访问时间 */
    private Instant accessedAt;
    /** success / expired / revoked / wrong_password / rate_limited */
    private String status;
}