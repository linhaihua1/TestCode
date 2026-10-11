package com.apiweb.vo.cijenkins;

import lombok.Data;

import java.time.Instant;

/**
 * Jenkins 服务器配置 VO（响应）。
 *
 * <p>不含 apiToken 字段，从源头杜绝 Token 外泄。</p>
 */
@Data
public class CiJenkinsServerVO {

    /** 配置 ID */
    private String id;
    /** 显示名 */
    private String name;
    /** 根地址 */
    private String baseUrl;
    /** API 用户名 */
    private String username;
    /** 认证方式 */
    private String authType;
    /** 是否启用 */
    private Boolean enabled;
    /** 备注说明 */
    private String description;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
