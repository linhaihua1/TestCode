package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * CI/CD 流水线执行记录。
 *
 * <p>对应表：{@code t_ci_pipeline_run}。
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>触发时创建，状态 {@code pending} / {@code running}</li>
 *   <li>本地执行：执行完用例后汇总 summary，并按流水线门禁规则判定 gatePassed</li>
 *   <li>Jenkins 执行：记录队列地址与构建号，轮询 Jenkins 直到构建结束，再映射结果</li>
 *   <li>落到终态（success / failed / error）后 CI 轮询即可结束</li>
 * </ol>
 *
 * <p>字段 {@code summary} / {@code gateResult} 保存本次执行的统计与门禁明细，
 * 供流水线运行详情页与 CI 日志输出展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ci_pipeline_run")
public class CiPipelineRunEntity extends BaseEntity {
    /** 所属流水线 ID */
    private String pipelineId;
    /** 所属项目 ID（冗余，便于按项目查询） */
    private String projectId;
    /** 流水线名称快照（流水线被删除后仍可追溯） */
    private String pipelineName;
    /** 测试类型：api / ui / perf */
    private String testType;
    /** 目标类型：test_task / ui_scenario / ui_test / perf_case */
    private String targetType;
    /** 目标 ID */
    private String targetId;
    /** 执行方式：local / jenkins */
    private String execMode;
    /** 执行状态：pending / running / success / failed / error */
    private String status;
    /** 触发方式：manual / webhook / api / schedule / jenkins */
    private String triggerType;
    /** 触发者标识（用户名 / Jenkins BUILD_TAG / commit sha 等） */
    private String triggerBy;
    /** 本次执行产生的报告 ID 列表（JSON 数组） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String reportIds;
    /** 执行统计（JSON 对象：total/passed/failed/error/passRate/avgResponseTime/p95ResponseTime 等） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String summary;
    /** 门禁判定明细（JSON 数组：[{metric, operator, threshold, actual, passed}]） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String gateResult;
    /** 是否通过质量门禁：1=通过，0=未通过，null=未判定 */
    private Integer gatePassed;
    /** 执行日志（异常堆栈 / 关键步骤） */
    private String log;

    // ----- Jenkins 执行相关 -----
    /** Jenkins 服务器 ID */
    private String jenkinsServerId;
    /** Jenkins Job 名称 */
    private String jenkinsJobName;
    /** Jenkins 队列项地址（用于解析构建号） */
    private String jenkinsQueueUrl;
    /** Jenkins 构建号 */
    private Integer jenkinsBuildNo;
    /** Jenkins 构建详情页地址 */
    private String jenkinsBuildUrl;
    /** Jenkins 构建结果（SUCCESS / FAILURE / UNSTABLE / ABORTED） */
    private String jenkinsResult;

    /** 耗时（毫秒） */
    private Integer duration;
    /** 开始时间 */
    private Instant startedAt;
    /** 结束时间（未结束为 null） */
    private Instant endedAt;
}
