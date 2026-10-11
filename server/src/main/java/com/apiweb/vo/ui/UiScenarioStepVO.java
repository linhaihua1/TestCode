package com.apiweb.vo.ui;

import lombok.Data;

/**
 * UI 场景步骤 VO（响应）。
 */
@Data
public class UiScenarioStepVO {

    private String id;
    private String scenarioId;
    private Integer sortOrder;
    private String uiTestCaseId;
}
