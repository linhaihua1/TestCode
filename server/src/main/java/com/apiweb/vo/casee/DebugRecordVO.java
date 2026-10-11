package com.apiweb.vo.casee;

import lombok.Data;

import java.time.Instant;

/**
 * 调试记录 VO（响应）。
 */
@Data
public class DebugRecordVO {

    private String id;
    private String caseId;
    private String caseNameSnapshot;
    private String environmentId;
    private String executeMode;
    private String result;
    private Integer totalDuration;
    private String requestSummary;
    private String responseSummary;
    private String assertionResults;
    private String extractedVariables;
    private String stepResults;
    private String requestBodyRef;
    private String responseBodyRef;
    private String errorLog;
    private String createdBy;
    private Instant createdAt;
}
