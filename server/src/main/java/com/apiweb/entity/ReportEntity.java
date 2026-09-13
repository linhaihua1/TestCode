package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 测试报告（接口/UI/性能通用）。
 *
 * <h3>核心字段</h3>
 * <ul>
 *   <li>{@code totalCases / passedCases / failedCases}：用例统计</li>
 *   <li>{@code totalAssertions / passedAssertions / failedAssertions}：断言统计</li>
 *   <li>{@code avgResponseTime / p95ResponseTime}：响应时间</li>
 *   <li>{@code triggerType}：manual（手动）/ schedule（定时）/ webhook（CI/CD）/ api（API 调用）</li>
 *   <li>{@code status}：pending / running / passed / failed / error</li>
 * </ul>
 */
@Data
@TableName("t_report")
public class ReportEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属项目 ID */
    private String projectId;
    /** 所属场景 ID */
    private String scenarioId;
    /** 关联任务 ID（可空,手动调试用例会直接生成 report 不经过 task） */
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
    /** 执行触发方式:manual / schedule / webhook / api */
    private String triggerType;
    /** 触发者用户名 */
    private String triggerBy;

    // ----- 统计字段（执行完成后回填） -----
    private Integer totalCases;
    /** 通过用例数 */
    private Integer passedCases;
    /** 失败用例数 */
    private Integer failedCases;
    /** 异常用例数 */
    private Integer errorCases;
    /** 跳过用例数 */
    private Integer skippedCases;
    /** 断言总数 */
    private Integer totalAssertions;
    /** 通过断言数 */
    private Integer passedAssertions;
    /** 失败断言数 */
    private Integer failedAssertions;
    /** 平均响应时间（毫秒） */
    private Integer avgResponseTime;
    /** 95 分位响应时间（毫秒） */
    private Integer p95ResponseTime;

    /** 测试环境 baseUrl（快照,便于报告还原） */
    private String environmentSnapshot;
}