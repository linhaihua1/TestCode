package com.apiweb.dto.casestep;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用例步骤请求 DTO（新建/更新）。
 */
@Data
public class CaseStepReq {

    /** 所属用例 ID（必填） */
    @NotBlank(message = "caseId 必填")
    private String caseId;

    /** 父步骤 id（null=顶层） */
    private String parentId;

    /** 步骤类型枚举值（HTTP_REQUEST / SCRIPT / ...）（必填） */
    @NotBlank(message = "stepType 必填")
    private String stepType;

    /** 步骤名（必填） */
    @NotBlank(message = "步骤名称必填")
    @Size(max = 200)
    private String name;

    /** 位置：PRE/TEST/POST */
    @Size(max = 16)
    private String position;

    /** 排序号 */
    private Integer sortOrder;

    /** 是否启用 */
    private Boolean enabled;

    /** 步骤备注 */
    @Size(max = 500)
    private String remark;

    /** 步骤配置（JSON） */
    private String config;

    /** 失败策略：stop / continue */
    @Size(max = 16)
    private String failStrategy;
}
