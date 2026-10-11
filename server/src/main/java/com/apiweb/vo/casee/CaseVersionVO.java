package com.apiweb.vo.casee;

import lombok.Data;

import java.time.Instant;

/**
 * 用例版本快照 VO（响应）。
 */
@Data
public class CaseVersionVO {

    private String id;
    private String caseId;
    private Integer version;
    private String snapshot;
    private String changeSummary;
    private String createdBy;
    private Instant createdAt;
}
