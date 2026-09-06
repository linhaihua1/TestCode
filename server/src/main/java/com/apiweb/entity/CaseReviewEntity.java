package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 用例评审记录 */
@Data
@TableName("t_case_review")
public class CaseReviewEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String caseId;
    private String reviewerId;
    private String reviewerName;
    /** submit / approve / reject */
    private String action;
    private String comment;
    private String fromStatus;
    private String toStatus;
    private Instant createdAt;
}
