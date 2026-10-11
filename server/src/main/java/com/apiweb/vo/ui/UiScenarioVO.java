package com.apiweb.vo.ui;

import lombok.Data;

import java.time.Instant;

/**
 * UI 执行场景 VO（响应）。
 */
@Data
public class UiScenarioVO {

    private String id;
    private String projectId;
    private String name;
    private String description;
    private String environmentId;
    private String variables;
    private String executorUrl;
    private String executorHost;
    private Integer executorPort;
    private Integer retryCount;
    private Integer timeoutMs;
    private Instant createdAt;
    private Instant updatedAt;
}
