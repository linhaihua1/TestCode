package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 环境配置 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_environment")
public class EnvironmentEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String baseUrl;
    /** JSON: [{key,value}] */
    private String variables;
    /** JSON: [{key,value}] */
    private String headers;
}
