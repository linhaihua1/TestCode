package com.apiweb.vo.casee;

import lombok.Data;

import java.time.Instant;

/**
 * 用例 VO（响应）。
 */
@Data
public class CaseVO {

    private String id;
    private String projectId;
    private String moduleId;
    private String name;
    private String description;
    private String status;
    private String priority;
    /** JSON: string[]，序列化为数组返回给前端 */
    private Object tags;
    /** JSON: 步骤定义数组，序列化为数组返回给前端 */
    private Object steps;
    private Integer version;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
