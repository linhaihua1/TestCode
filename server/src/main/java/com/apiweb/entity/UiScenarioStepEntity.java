package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** UI 场景步骤（引用 UI 用例） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ui_scenario_step")
public class UiScenarioStepEntity extends BaseEntity {
    private String scenarioId;
    private Integer sortOrder;
    private String uiTestCaseId;
}
