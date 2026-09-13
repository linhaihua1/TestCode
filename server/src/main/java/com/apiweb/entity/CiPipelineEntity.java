package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * CI/CD 流水线：把「接口自动化 / UI 自动化 / 性能测试」的一类执行目标包装成
 * 可被 Jenkins 或外部 CI 系统触发的流水线。
 *
 * <p>对应表：{@code t_ci_pipeline}。
 *
 * <h3>触发方式</h3>
 * <ul>
 *   <li>平台页面手动触发（{@code POST /api/v1/ci/pipelines/{id}/trigger}）</li>
 *   <li>免登录 webhook 触发（{@code POST /api/v1/webhook/ci/pipelines/trigger?token=xxx}），
 *       供 Jenkins Pipeline / Shell 步骤直接 curl 调用</li>
 * </ul>
 *
 * <h3>执行方式</h3>
 * <ul>
 *   <li>{@code local}：平台本地执行，结果计入平台报告并做质量门禁判定</li>
 *   <li>{@code jenkins}：平台调用 Jenkins Remote API 触发远端 Job，
 *       由 Jenkins 侧完成执行（可再回调平台），平台只跟踪构建状态</li>
 * </ul>
 *
 * <h3>质量门禁</h3>
 * {@code qualityGate} 为规则数组 {@code [{metric, operator, threshold}]}，
 * 任一条规则违规即判定流水线「未通过」，CI 可据此让构建失败。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ci_pipeline")
public class CiPipelineEntity extends BaseEntity {
    /** 所属项目 ID */
    private String projectId;
    /** 流水线名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 测试类型：api / ui / perf（见 PipelineTestType） */
    private String testType;
    /** 目标类型：test_task / ui_scenario / ui_test / perf_case（见 PipelineTargetType） */
    private String targetType;
    /** 目标 ID（测试任务 / UI 场景 / UI 用例 / 性能用例） */
    private String targetId;
    /** 执行方式：local / jenkins（见 PipelineExecMode） */
    private String execMode;
    /** Jenkins 服务器 ID（execMode=jenkins 时必填） */
    private String jenkinsServerId;
    /** Jenkins Job 名称（支持 folder/job 形式） */
    private String jenkinsJobName;
    /** 触发 Jenkins 时附加的构建参数（JSON 对象：{key: value}） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String jenkinsParams;
    /** 质量门禁规则（JSON 数组：[{metric, operator, threshold}]） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String qualityGate;
    /** 通知渠道配置（JSON 数组：[{type, target, secret}]） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String notifyChannels;
    /** 是否启用 */
    private Boolean enabled;
    /** webhook Token（随机串，供 CI 免登录触发） */
    private String webhookToken;
    /** 是否启用 webhook 触发 */
    private Boolean webhookEnabled;
    /** 创建人 */
    private String createdBy;
    /** 软删除时间：null 表示未删除 */
    private Instant deletedAt;
}
