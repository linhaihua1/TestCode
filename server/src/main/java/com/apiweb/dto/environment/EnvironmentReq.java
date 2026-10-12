package com.apiweb.dto.environment;

import com.apiweb.util.JsonStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/更新环境请求 DTO。
 *
 * <p>与 {@code EnvironmentEntity} 解耦：只暴露可写字段（projectId/name/baseUrl/variables/headers），
 * 避免把内部字段直接暴露为 API 入参。</p>
 */
@Data
public class EnvironmentReq {

    /** 所属项目 ID（必填） */
    @NotBlank(message = "projectId 必填")
    private String projectId;

    /** 环境名称（必填） */
    @NotBlank(message = "name 必填")
    @Size(max = 100)
    private String name;

    /** 环境 baseUrl（可选） */
    @Size(max = 500)
    private String baseUrl;

    /** JSON: [{key,value}]，缺省补空数组 */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String variables;

    /** JSON: [{key,value}]，缺省补空数组 */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String headers;
}
