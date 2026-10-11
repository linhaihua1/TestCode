package com.apiweb.dto.ci;

import lombok.Data;

/**
 * CI 流水线触发请求体。
 */
@Data
public class CiPipelineTriggerReq {

    /** 是否阻塞等待执行完成（默认 false，立即返回 runId） */
    private Boolean wait;

    /** 触发者标识（页面触发时留空则用当前登录用户名） */
    private String triggerBy;
}
