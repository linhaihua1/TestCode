package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_project")
public class ProjectEntity extends BaseEntity {
    private String name;
    private String description;
}
