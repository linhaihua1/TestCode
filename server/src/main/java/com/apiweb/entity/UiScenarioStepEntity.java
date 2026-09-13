package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * UI 场景步骤（引用 UI 用例）
 *
 * <p>对应表：{@code t_ui_scenario_step}。
 * UI 场景与 UI 用例的关联关系，sortOrder 决定执行顺序。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ui_scenario_step")
public class UiScenarioStepEntity extends BaseEntity {
    /** 所属场景 ID */
    private String scenarioId;
    /** 排序号（升序） */
    private Integer sortOrder;
    /** 引用的 UI 用例 ID */
    private String uiTestCaseId;
}
