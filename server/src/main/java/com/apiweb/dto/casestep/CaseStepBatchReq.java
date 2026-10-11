package com.apiweb.dto.casestep;

import lombok.Data;

import java.util.List;

/**
 * 批量保存步骤请求（前端拖拽后整体提交）。
 */
@Data
public class CaseStepBatchReq {

    /** 所属用例 ID（必填） */
    private String caseId;

    /** 步骤数组（已含父子关系） */
    private List<CaseStepReq> steps;
}
