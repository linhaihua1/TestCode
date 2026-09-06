package com.apiweb.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户。
 *
 * <p>由 {@link AuthInterceptor} 在每次请求时从 JWT 解析得到，写入 {@link UserContext}（ThreadLocal）。
 * 业务代码通过 {@link UserContext#getCurrentUser()} 获取，无需自行解析 token。
 */
@Data
@AllArgsConstructor
public class CurrentUser {
    /** 用户 ID（对应 t_user.id） */
    private String userId;
    /** 用户名（登录账号） */
    private String username;
    /** 角色：admin / manager / engineer / viewer */
    private String role;

    /**
     * 是否为系统管理员。
     * 管理员拥有所有权限，可访问 /api/users、/api/audit-logs 等管理接口。
     */
    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
