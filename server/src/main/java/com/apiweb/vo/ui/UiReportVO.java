package com.apiweb.vo.ui;

import lombok.Data;

import java.time.Instant;

/**
 * UI 测试报告 VO（响应）。
 */
@Data
public class UiReportVO {

    private String id;
    private String projectId;
    private String testCaseId;
    private String name;
    private String status;
    private Integer duration;
    private Instant startedAt;
    private String details;
    private String detailsRef;
}
