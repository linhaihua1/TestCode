package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用例评审记录
 *
 * <p>对应表：{@code t_case_review}。
 * 评审动作流水：submit（提交评审）/ approve（通过）/ reject（驳回），
 * 同时记录状态迁移 fromStatus → toStatus。</p>
 */
@Data
@TableName("t_case_review")
public class CaseReviewEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属用例 ID */
    private String caseId;
    /** 评审人 ID */
    private String reviewerId;
    /** 评审人姓名 */
    private String reviewerName;
    /** submit / approve / reject */
    private String action;
    /** 评审意见 */
    private String comment;
    /** 变更前状态 */
    private String fromStatus;
    /** 变更后状态 */
    private String toStatus;
    /** 创建时间（UTC） */
    private Instant createdAt;
}
