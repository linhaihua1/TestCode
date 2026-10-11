package com.apiweb.dto.scenario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 场景请求 DTO（新建/更新，含步骤）。
 */
@Data
public class ScenarioReq {

    /** 所属项目 ID（必填） */
    @NotBlank(message = "projectId 必填")
    private String projectId;

    /** 场景名称（可选，默认"未命名场景"） */
    @Size(max = 200)
    private String name;

    /** 描述（可选） */
    @Size(max = 2000)
    private String description;

    /** 步骤数组（可选） */
    private List<StepReq> steps;

    /** 步骤请求 */
    @Data
    public static class StepReq {
        /** 引用的接口用例 ID */
        private String apiCaseId;
        /** 名称 */
        private String name;
        /** 断言配置（JSON） */
        private List<Object> assertions;
        /** 变量提取配置（JSON） */
        private List<Object> extracts;
    }
}
