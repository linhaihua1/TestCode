package com.apiweb.dto.ui;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * UI 测试用例请求 DTO（新建/更新）。
 */
@Data
public class UiTestCaseReq {

    /** 所属项目 ID */
    private String projectId;

    /** 名称（必填） */
    @NotBlank(message = "用例名称必填")
    @Size(max = 200)
    private String name;

    /** 描述 */
    private String description;

    /** 环境 baseUrl */
    private String baseUrl;

    /** 前置步骤（JSON 数组） */
    private String setupSteps;

    /** 测试步骤（JSON 数组） */
    private String steps;

    /** 后置步骤（JSON 数组） */
    private String teardownSteps;
}
