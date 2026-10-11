package com.apiweb.vo.ui;

import lombok.Data;

import java.time.Instant;

/**
 * UI 测试用例 VO（响应）。
 */
@Data
public class UiTestCaseVO {

    private String id;
    private String projectId;
    private String name;
    private String description;
    private String baseUrl;
    private String setupSteps;
    private String steps;
    private String teardownSteps;
    private Instant createdAt;
    private Instant updatedAt;
}
