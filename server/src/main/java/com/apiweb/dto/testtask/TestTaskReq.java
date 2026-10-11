package com.apiweb.dto.testtask;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 测试任务请求 DTO（新建/更新）。
 */
@Data
public class TestTaskReq {

    /** 所属项目 ID（必填） */
    @NotBlank(message = "projectId 必填")
    private String projectId;

    /** 名称（必填） */
    @NotBlank(message = "任务名称必填")
    @Size(max = 200)
    private String name;

    /** 描述 */
    @Size(max = 2000)
    private String description;

    /** 用例 ID 列表（JSON 数组） */
    private String caseIds;

    /** 执行环境 ID */
    private String environmentId;

    /** sequential / parallel */
    private String executeMode;

    /** stop_on_fail / continue_all / retry_then_stop */
    private String failStrategy;

    /** 并行池大小（仅 parallel 模式生效） */
    private Integer parallelPoolSize;

    /** 失败重试次数 */
    private Integer retryCount;

    /** 执行超时时间（毫秒） */
    private Integer timeoutMs;

    /** Quartz cron 表达式 */
    private String cronExpr;

    /** 是否启用 */
    private Boolean enabled;

    /** 通知回调地址 */
    private String notifyUrl;

    /** 变量（JSON 数组） */
    private String variables;

    /** 环境 baseUrl */
    private String baseUrl;

    // ----- 执行机参数 -----
    private String executorUrl;
    private String executorHost;
    private Integer executorPort;

    // ----- CI/CD webhook -----
    private String webhookToken;
    private Boolean webhookEnabled;
    private Boolean webhookAutoExecute;

    // ----- 通知配置 -----
    private String notifyChannels;
}
