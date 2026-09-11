package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 接口定义（按开发文档 §2.1 ApiDefinition 字段完整化）。
 *
 * <p>字段分类：
 * <ul>
 *   <li>基础：projectId/moduleId/name/method/path/description/tags</li>
 *   <li>请求参数：pathParams/queryParams/headers/bodyType/bodySchema/body</li>
 *   <li>响应：responseExamples/responseHeaders</li>
 *   <li>Mock：mockEnabled/mockType/mockResponse/mockStatusCode/mockRules(mockType=CONDITIONAL)</li>
 *   <li>导入：sourceUrl/sourceHash(用于同步差异)</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_api_definition")
public class ApiDefinitionEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String method;
    private String path;
    /** 路径参数定义：JSON [{key,value,description}]（如 /users/{id} → [{key:id,description:用户ID}]） */
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
    /** 请求体 Schema：JSON（OpenAPI schema 风格） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String bodySchema;
    /** 请求体示例：JSON 字符串 */
    private String body;
    /** 响应示例：JSON { "200": {...}, "400": {...}, "default": {...} } */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String responseExamples;
    /** 响应头：JSON {key:value} */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String responseHeaders;
    private String description;
    private String moduleId;
    /** 标签：JSON ["用户管理","订单"] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String tags;

    // ----- Mock -----
    private Boolean mockEnabled;
    /**
     * Mock 类型：
     * <ul>
     *   <li>BASIC：直接返回 {@link #mockResponse}</li>
     *   <li>CONDITIONAL：根据请求参数匹配 mockRules,命中对应响应</li>
     *   <li>DYNAMIC：JS 脚本动态生成响应</li>
     *   <li>SMART：智能匹配(根据请求 method+path 自动选 responseExamples 中的示例)</li>
     * </ul>
     */
    private String mockType;
    /** Mock 状态码(默认 200) */
    private Integer mockStatusCode;
    /** Mock 响应体(BASIC 类型) */
    private String mockResponse;
    /** Mock 规则(CONDITIONAL 类型)：JSON [{when:{key,op,value}, response:{}, status:200}] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String mockRules;
    /** Mock 脚本(DYNAMIC 类型)：JS 代码 */
    private String mockScript;

    // ----- 来源(用于同步差异) -----
    /** 来源 URL(Swagger 导入时填写) */
    private String sourceUrl;
    /** 来源内容的 SHA-256(同步差异用) */
    private String sourceHash;

    /** 软删除时间：null 表示未删除，进入回收站时设置 */
    @TableField("deleted_at")
    private Instant deletedAt;
}