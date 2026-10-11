package com.apiweb.vo.auditlog;

import lombok.Data;

import java.time.Instant;

/**
 * 审计日志 VO（响应）。
 */
@Data
public class AuditLogVO {

    /** 主键 ID */
    private String id;
    /** 操作用户 ID */
    private String userId;
    /** 操作用户名 */
    private String username;
    /** 操作动作 */
    private String action;
    /** 被操作实体类型 */
    private String entityType;
    /** 被操作实体 ID */
    private String entityId;
    /** 变更前数据快照（JSON） */
    private String beforeJson;
    /** 变更后数据快照（JSON） */
    private String afterJson;
    /** 操作来源 IP */
    private String ip;
    /** 创建时间（UTC） */
    private Instant createdAt;
}
