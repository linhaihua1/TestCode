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
    /** 用户自定义变量（JSON 字符串解析后的结构化对象） */
    private Object variables;
    /** HTTP 请求步骤（JSON 字符串解析后的数组） */
    private Object steps;
    /** 压测配置（JSON 字符串解析后的对象） */
    private Object profile;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
