package com.apiweb.dto.apidefinition;

import lombok.Data;

/**
 * Swagger URL 导入请求 DTO。
 */
@Data
public class ApiDefinitionImportReq {
    /** Swagger 文档 URL */
    private String url;
    /** 目标项目 ID（必填） */
    private String projectId;
    /** 目标模块 ID（可选） */
    private String moduleId;
}
