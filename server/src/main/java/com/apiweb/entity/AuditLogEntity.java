package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 操作审计日志
 *
 * <p>对应表：{@code t_audit_log}。
 * 由 AuditAspect 切面在带 @AuditLog 的接口方法执行成功后写入，
 * 记录操作人、动作、实体前后快照与来源 IP，用于安全审计与追溯。</p>
 */
@Data
@TableName("t_audit_log")
public class AuditLogEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 操作用户 ID */
    private String userId;
    /** 操作用户名（冗余存储，用户注销后仍可追溯） */
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
