package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 接口定义 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_api_definition")
public class ApiDefinitionEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String method;
    private String path;
    /** JSON: [{key,value,enabled}] */
    private String headers;
    /** JSON: [{key,value,enabled}] */
    private String query;
    private String body;
    private String description;
    private Boolean mockEnabled;
    private String mockResponse;
    private String moduleId;
    /** JSON: string[] */
    private String tags;
}
