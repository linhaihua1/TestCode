package com.apiweb.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/更新项目请求 DTO。
 */
@Data
public class ProjectReq {

    /** 项目名称（必填） */
    @NotBlank(message = "项目名称必填")
    @Size(max = 200)
    private String name;

    /** 项目描述（可选） */
    @Size(max = 2000)
    private String description;
}
