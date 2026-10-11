package com.apiweb.dto.casee;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用例评审动作请求 DTO。
 */
@Data
public class CaseReviewReq {

    /** 评审动作：submit / approve / reject（默认 submit） */
    private String action;

    /** 评审意见 */
    private String comment;
}
