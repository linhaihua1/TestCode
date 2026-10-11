package com.apiweb.vo.perf;

import lombok.Data;

import java.time.Instant;

/**
 * 性能用例 VO（响应）。
 */
@Data
public class PerfCaseVO {

    private String id;
    private String projectId;
    private String name;
    private String description;
    private Integer threads;
    private Integer rampUp;
    private Integer loops;
    private Integer duration;
    private Integer thinkTime;
    private String onSampleError;
    private String variables;
    private String steps;
    private String profile;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
