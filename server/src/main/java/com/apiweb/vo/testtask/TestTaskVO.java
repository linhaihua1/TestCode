package com.apiweb.vo.testtask;

import lombok.Data;

import java.time.Instant;

/**
 * 测试任务 VO（响应）。
 */
@Data
public class TestTaskVO {

    private String id;
    private String projectId;
    private String name;
    private String description;
    private Object caseIds;
    private String environmentId;
    private String executeMode;
    private String failStrategy;
    private Integer parallelPoolSize;
    private Integer retryCount;
    private Integer timeoutMs;
    private String cronExpr;
    private Boolean enabled;
    private String notifyUrl;
    private Object variables;
    private String baseUrl;
    private String createdBy;

    // ----- 执行机参数 -----
    private String executorUrl;
    private String executorHost;
    private Integer executorPort;

    // ----- CI/CD webhook -----
    private String webhookToken;
    private Boolean webhookEnabled;
    private Boolean webhookAutoExecute;

    // ----- 通知配置 -----
    private Object notifyChannels;

    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
