package com.apiweb.dto.casee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用例请求 DTO（新建/更新）。
 *
 * <p>字段名与旧实体保持一致，保证前端零改动。</p>
 */
@Data
public class CaseReq {

    /** 所属项目 ID */
    private String projectId;

    /** 所属模块 ID */
    private String moduleId;

    /** 名称（必填） */
    @NotBlank(message = "用例名称必填")
    @Size(max = 200)
    private String name;

    /** 描述 */
    private String description;

    /** draft / reviewing / pass / fail / trash */
    private String status;

    /** 优先级 */
    private String priority;

    /** JSON: string[] */
    private String tags;

    /** JSON: 多步骤定义 */
    private String steps;

    /** 版本号 */
    private Integer version;

    /** 排序号 */
    private Integer sortOrder;
}
