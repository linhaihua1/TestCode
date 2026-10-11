package com.apiweb.dto.globalvariable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/更新全局变量请求 DTO。
 */
@Data
public class GlobalVariableReq {

    /** 所属项目 ID（必填） */
    @NotBlank(message = "projectId 必填")
    private String projectId;

    /** 变量名（必填） */
    @NotBlank(message = "变量名必填")
    @Size(max = 100)
    private String name;

    /** 类型：STRING/NUMBER/BOOLEAN/TIMESTAMP/RANDOM/SECRET */
    @Size(max = 32)
    private String type;

    /** 变量值（SECRET 类型入库前加密；TIMESTAMP/RANDOM 由后端生成） */
    private String value;

    /** 是否加密存储 */
    private Boolean encrypted;

    /** 描述（可选） */
    @Size(max = 500)
    private String description;
}
