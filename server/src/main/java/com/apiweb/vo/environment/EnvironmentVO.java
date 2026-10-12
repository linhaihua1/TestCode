package com.apiweb.vo.environment;

import lombok.Data;

import java.time.Instant;

/**
 * 环境 VO（响应）。
 */
@Data
public class EnvironmentVO {

    /** 环境 ID */
    private String id;
    /** 所属项目 ID */
    private String projectId;
    /** 名称 */
    private String name;
    /** 环境 baseUrl */
    private String baseUrl;
    /** JSON: [{key,value}]，序列化为数组返回 */
    private Object variables;
    /** JSON: [{key,value}]，序列化为数组返回 */
    private Object headers;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
