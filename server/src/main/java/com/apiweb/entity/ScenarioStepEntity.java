package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 场景步骤
 *
 * <p>对应表：{@code t_scenario_step}。
 * 场景中的一个步骤，引用一个接口用例并可覆盖其断言与变量提取配置。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_scenario_step")
public class ScenarioStepEntity extends BaseEntity {
    /** 所属场景 ID */
    private String scenarioId;
    /** 排序号（升序） */
    private Integer sortOrder;
    /** 引用的接口用例 ID */
    private String apiCaseId;
    /** 名称 */
    private String name;
    /** 断言配置（JSON） */
    private String assertions;
    /** 变量提取配置（JSON） */
    private String extracts;
}
