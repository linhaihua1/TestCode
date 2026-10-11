package com.apiweb.vo.casestep;

import lombok.Data;

import java.time.Instant;

/**
 * 用例步骤 VO（响应）。
 */
@Data
public class CaseStepVO {

    /** 步骤 ID */
    private String id;
    /** 所属用例 */
    private String caseId;
    /** 父步骤 id */
    private String parentId;
    /** 步骤类型 */
    private String stepType;
    /** 步骤名 */
    private String name;
    /** 位置：PRE/TEST/POST */
    private String position;
    /** 排序号 */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean enabled;
    /** 步骤备注 */
    private String remark;
    /** 步骤配置（JSON） */
    private String config;
    /** 失败策略 */
    private String failStrategy;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
