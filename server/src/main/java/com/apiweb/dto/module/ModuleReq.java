package com.apiweb.dto.module;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/更新模块请求 DTO。
 */
@Data
public class ModuleReq {

    /** 所属项目 ID（必填） */
    @NotBlank(message = "projectId 必填")
    private String projectId;

    /** 父节点 ID（null 表示顶层） */
    private String parentId;

    /** 模块名称（必填） */
    @NotBlank(message = "模块名称必填")
    @Size(max = 100)
    private String name;

    /** case / api / ui / perf */
    @Size(max = 32)
    private String type;

    /** 排序号（升序） */
    private Integer sortOrder;
}
