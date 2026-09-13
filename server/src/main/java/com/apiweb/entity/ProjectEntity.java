package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目
 *
 * <p>对应表：{@code t_project}。
 * 项目是全部测试资产与报告的顶层隔离单元。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_project")
public class ProjectEntity extends BaseEntity {
    /** 名称 */
    private String name;
    /** 描述 */
    private String description;
}
