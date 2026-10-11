package com.apiweb.vo.webhook;

import lombok.Data;

import java.util.List;

/**
 * Webhook 触发响应 VO。
 */
@Data
public class WebhookTriggerVO {

    /** 任务 ID */
    private String taskId;
    /** 任务名称 */
    private String taskName;
    /** 本次执行的会话 ID（16 位） */
    private String execId;
    /** 触发时间 */
    private String triggeredAt;
    /** 触发者标识 */
    private String triggeredBy;
    /** 产生的 run 数量 */
    private int runCount;
    /** run ID 列表 */
    private List<String> runIds;
}
