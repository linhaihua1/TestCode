package com.apiweb.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户（由 AuthInterceptor 写入 ThreadLocal）。
 */
@Data
@AllArgsConstructor
public class CurrentUser {
    private String userId;
    private String username;
    private String role;

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
