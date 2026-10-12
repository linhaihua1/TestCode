package com.apiweb.dto.apidefinition;

import com.apiweb.util.JsonStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 接口定义请求 DTO（新建/更新）。
 *
 * <p>字段名与旧实体保持一致，保证前端零改动。仅暴露可写业务字段，
 * 不含 sourceHash、deletedAt 等系统维护字段。</p>
 *
 * <p>JSON 字符串字段（pathParams/query/headers 等）统一标注
 * {@link JsonStringDeserializer}，前端以数组/对象提交时自动序列化为 JSON 字符串，
 * 与 {@code ApiDefinitionEntity} 保持一致。</p>
 */
@Data
public class ApiDefinitionReq {

    /** 所属项目 ID */
    private String projectId;

    /** 名称（必填） */
    @NotBlank(message = "接口名称必填")
    @Size(max = 200)
    private String name;

    /** HTTP 方法（GET/POST/PUT/DELETE 等，必填） */
    @NotBlank(message = "method 必填")
    @Size(max = 16)
    private String method;

    /** 接口路径（必填） */
    @NotBlank(message = "path 必填")
    @Size(max = 500)
    private String path;

    /** 路径参数定义：JSON [{key,value,description}] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String pathParams;

    /** Query 参数定义：JSON [{key,value,enabled,description,required}] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String query;

    /** 请求头定义：JSON [{key,value,enabled,description}] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String headers;

    /** 请求体类型：NONE/FORM_DATA/X_WWW_FORM_URLENCODED/JSON/XML/BINARY */
    private String bodyType;

    /** 请求体 Schema：JSON */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String bodySchema;

    /** 请求体示例：JSON 字符串 */
    private String body;

    /** 响应示例：JSON */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String responseExamples;

    /** 响应头：JSON */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String responseHeaders;

    /** 描述 */
    private String description;

    /** 所属模块 ID */
    private String moduleId;

    /** 标签：JSON */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String tags;

    // ----- Mock -----
    private Boolean mockEnabled;
    private String mockType;
    private Integer mockStatusCode;
    private String mockResponse;
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String mockRules;
    private String mockScript;

    // ----- 来源 -----
    private String sourceUrl;
    private String sourceHash;
}
