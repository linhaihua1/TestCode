package com.apiweb.vo.apidefinition;

import lombok.Data;

import java.time.Instant;

/**
 * 接口定义 VO（响应）。
 *
 * <p>字段名与旧实体保持一致，保证前端零改动。不含 sourceHash 等内部字段。</p>
 */
@Data
public class ApiDefinitionVO {

    private String id;
    private String projectId;
    private String name;
    private String method;
    private String path;
    private String pathParams;
    private String query;
    private String headers;
    private String bodyType;
    private String bodySchema;
    private String body;
    private String responseExamples;
    private String responseHeaders;
    private String description;
    private String moduleId;
    private String tags;

    // Mock
    private Boolean mockEnabled;
    private String mockType;
    private Integer mockStatusCode;
    private String mockResponse;
    private String mockRules;
    private String mockScript;

    // 来源
    private String sourceUrl;

    private Instant createdAt;
    private Instant updatedAt;
}
