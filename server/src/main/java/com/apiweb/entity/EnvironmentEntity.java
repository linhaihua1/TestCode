package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 环境配置
 *
 * <p>对应表：{@code t_environment}。
 * 一套环境 = baseUrl + 环境变量 + 公共请求头；执行时与任务/全局变量合并后供步骤引用。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_environment")
public class EnvironmentEntity extends BaseEntity {
    /** 所属项目 ID */
    private String projectId;
    /** 名称 */
    private String name;
    /** 环境 baseUrl */
    private String baseUrl;
    /** JSON: [{key,value}] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String variables;
    /** JSON: [{key,value}] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String headers;
}
