package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String variables;
    /** JSON: [{key,value}] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String headers;
}
