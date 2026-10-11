package com.apiweb.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建用户请求 DTO。
 *
 * <p>与 {@code UserEntity} 解耦：只暴露创建用户所需字段，
 * 避免把内部字段（如 passwordHash）直接暴露为 API 入参。</p>
 */
@Data
public class UserCreateReq {

    /** 用户名（必填） */
    @NotBlank(message = "用户名必填")
    private String username;

    /** 明文密码（必填，长度由 Service 校验 minPasswordLength） */
    @NotBlank(message = "密码必填")
    private String password;

    /** 角色（可选，默认 member），取值见 security.Role */
    @Size(max = 32)
    private String role;
}
