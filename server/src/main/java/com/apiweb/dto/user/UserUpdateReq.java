package com.apiweb.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户请求 DTO（可改 role 或重置密码，未传字段保持原值）。
 */
@Data
public class UserUpdateReq {

    /** 角色（可选），取值见 security.Role */
    @Size(max = 32)
    private String role;

    /** 明文密码（可选，重置时长度由 Service 校验） */
    @Size(min = 1, message = "密码不能为空字符串")
    private String password;
}
