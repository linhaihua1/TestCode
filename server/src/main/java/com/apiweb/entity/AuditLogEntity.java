package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 操作审计日志 */
@Data
@TableName("t_audit_log")
public class AuditLogEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String username;
    private String action;
    private String entityType;
    private String entityId;
    private String beforeJson;
    private String afterJson;
    private String ip;
    private Instant createdAt;
}
