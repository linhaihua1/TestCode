package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 全局变量 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_global_variable")
public class GlobalVariableEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String type;
    private String value;
    private Boolean encrypted;
    private String description;
}
