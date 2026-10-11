package com.apiweb.dto.casestep;

import lombok.Data;

import java.util.List;

/**
 * 步骤排序请求。
 */
@Data
public class CaseStepReorderReq {

    /** 父节点 ID（可选） */
    private String parentId;

    /** 目标顺序的步骤 ID 列表（必填且非空） */
    private List<String> stepIds;
}
