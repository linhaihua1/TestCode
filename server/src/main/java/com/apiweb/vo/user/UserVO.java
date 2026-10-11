package com.apiweb.vo.user;

import lombok.Data;

import java.time.Instant;

/**
 * 用户 VO（响应）。
 *
 * <p>与 {@code UserEntity} 解耦：不含 passwordHash 等敏感字段，
 * 表结构变更不会直接冲击 API 契约。</p>
 */
@Data
public class UserVO {

    /** 用户 ID */
    private String id;
    /** 用户名 */
    private String username;
    /** 角色：admin / member / viewer */
    private String role;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
