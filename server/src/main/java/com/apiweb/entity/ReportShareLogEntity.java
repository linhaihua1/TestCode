package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 报告分享访问日志（审计用途）。
 */
@Data
@TableName("t_report_share_log")
public class ReportShareLogEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String shareId;
    private String reportId;
    private String accessIp;
    private String userAgent;
    private Instant accessedAt;
    /** success / expired / revoked / wrong_password / rate_limited */
    private String status;
}