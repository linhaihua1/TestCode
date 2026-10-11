package com.apiweb.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改密码请求 DTO。
 */
@Data
public class ChangePasswordReq {

    /** 原密码（必填） */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码（必填） */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
