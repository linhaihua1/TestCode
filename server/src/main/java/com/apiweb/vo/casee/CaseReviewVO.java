package com.apiweb.vo.casee;

import lombok.Data;

import java.time.Instant;

/**
 * 用例评审记录 VO（响应）。
 */
@Data
public class CaseReviewVO {

    private String id;
    private String caseId;
    private String reviewerId;
    private String reviewerName;
    private String action;
    private String comment;
    private String fromStatus;
    private String toStatus;
    private Instant createdAt;
}
