package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 测试任务。
 *
 * <h3>关键字段</h3>
 * <ul>
 *   <li>{@code executeMode}：sequential（顺序）/ parallel（并行）</li>
 *   <li>{@code failStrategy}：stop_on_fail（任一失败停止）/ continue_all（全部跑完）/ retry_then_stop（重试后失败停止）</li>
 *   <li>{@code parallelPoolSize}：并行池大小（1-200，默认 5）</li>
 *   <li>{@code webhookToken}：CI/CD 触发所需的 Token（明文存储仅用于匹配,生产应加盐哈希）</li>
 *   <li>{@code webhookEnabled}：是否允许外部 webhook 触发</li>
 *   <li>{@code cronExpr}：Quartz cron 表达式</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_test_task")
public class TestTaskEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String description;
    /** JSON: string[] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String caseIds;
    private String environmentId;
    /** sequential / parallel */
    private String executeMode;
    /** stop_on_fail / continue_all / retry_then_stop */
    private String failStrategy;
    /** 并行池大小（仅 parallel 模式生效） */
    private Integer parallelPoolSize;
    private Integer retryCount;
    private Integer timeoutMs;
    private String cronExpr;
    private Boolean enabled;
    private String notifyUrl;
    /** JSON: [{key,value}] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String variables;
    private String baseUrl;
    private String createdBy;
    private Instant deletedAt;

    // ----- 执行机参数 -----
    /** 执行机完整地址（http://host:port），可选 */
    private String executorUrl;
    /** 执行机域名/IP */
    private String executorHost;
    /** 执行机端口 */
    private Integer executorPort;

    // ----- CI/CD webhook 字段 -----
    /** webhook Token（32 字符随机串,留空则禁用 webhook） */
    private String webhookToken;
    /** 是否启用 webhook 触发（默认 false） */
    private Boolean webhookEnabled;
    /** webhook 触发后是否自动执行（true）还是仅创建 run（false） */
    private Boolean webhookAutoExecute;

    // ----- 通知配置 -----
    /** 失败时通知（JSON 数组:[{type:email/dingtalk/feishu/webhook,target,secret}]） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String notifyChannels;
}