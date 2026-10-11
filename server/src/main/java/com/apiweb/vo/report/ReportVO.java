package com.apiweb.vo.report;

import lombok.Data;

import java.time.Instant;

/**
 * 测试报告 VO（响应）。
 */
@Data
public class ReportVO {

    /** 报告 ID */
    private String id;
    /** 所属项目 ID */
    private String projectId;
    /** 所属场景 ID */
    private String scenarioId;
    /** 关联任务 ID */
    private String taskId;
    /** 名称 */
    private String name;
    /** 状态 */
    private String status;
    /** 耗时（毫秒） */
    private Integer duration;
    /** 开始执行时间 */
    private Instant startedAt;
    /** 执行结束时间 */
    private Instant finishedAt;
    /** 触发方式 */
    private String triggerType;
    /** 触发者用户名 */
    private String triggerBy;

    // ----- 统计字段 -----
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private Integer errorCases;
    private Integer skippedCases;
    private Integer totalAssertions;
    private Integer passedAssertions;
    private Integer failedAssertions;
    private Integer avgResponseTime;
    private Integer p95ResponseTime;

    /** 测试环境 baseUrl（快照） */
    private String environmentSnapshot;
}
