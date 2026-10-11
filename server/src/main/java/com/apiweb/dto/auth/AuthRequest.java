package com.apiweb.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录/注册请求 DTO（用户名 + 密码）。
 */
@Data
public class AuthRequest {

    /** 用户名（必填） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（必填） */
    @NotBlank(message = "密码不能为空")
    private String password;
}
