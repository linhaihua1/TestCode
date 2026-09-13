package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户
 *
 * <p>对应表：{@code t_user}。
 * 系统用户；密码以 BCrypt 哈希存储，角色决定接口写权限（见 security.Role）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class UserEntity extends BaseEntity {
    /** 操作用户名（冗余存储，用户注销后仍可追溯） */
    private String username;
    /** BCrypt 密码哈希（永不明文存储） */
    private String passwordHash;
    /** admin / member / viewer */
    private String role;
}
