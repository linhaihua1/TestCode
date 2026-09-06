package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 场景步骤 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_scenario_step")
public class ScenarioStepEntity extends BaseEntity {
    private String scenarioId;
    private Integer sortOrder;
    private String apiCaseId;
    private String name;
    private String assertions;
    private String extracts;
}
