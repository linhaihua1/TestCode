package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** UI 执行场景 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ui_scenario")
public class UiScenarioEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String description;
}
