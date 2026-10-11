package com.apiweb.dto.cijenkins;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Jenkins 服务器配置请求 DTO。
 */
@Data
public class CiJenkinsServerReq {

    /** Jenkins 显示名（必填） */
    @NotBlank(message = "名称必填")
    @Size(max = 100)
    private String name;

    /** Jenkins 根地址（必填） */
    @NotBlank(message = "Jenkins 地址必填")
    @Size(max = 500)
    private String baseUrl;

    /** 调用 API 的用户名 */
    @Size(max = 100)
    private String username;

    /** API Token（明文入参，落库前加密；更新时留空表示不修改） */
    private String apiToken;

    /** 认证方式：basic / token */
    @Size(max = 16)
    private String authType;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注说明 */
    @Size(max = 500)
    private String description;
}
