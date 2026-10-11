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
    private String tags;
    private String steps;
    private Integer version;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
