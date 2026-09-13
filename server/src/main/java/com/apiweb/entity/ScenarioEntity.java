package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 接口场景
 *
 * <p>对应表：{@code t_scenario}。
 * 场景 = 多个接口用例的有序组合（见 t_scenario_step），一次执行生成一份报告。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_scenario")
public class ScenarioEntity extends BaseEntity {
    /** 所属项目 ID */
    private String projectId;
    /** 名称 */
    private String name;
    /** 描述 */
    private String description;
}
