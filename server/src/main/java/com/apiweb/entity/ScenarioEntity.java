package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 接口场景 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_scenario")
public class ScenarioEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String description;
}
