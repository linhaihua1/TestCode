package com.apiweb.dto.ui;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * UI 执行场景请求 DTO（新建/更新）。
 *
 * <p>steps 为引用 UI 用例 ID 列表（含 uiTestCaseId）。</p>
 */
@Data
public class UiScenarioReq {

    /** 所属项目 ID */
    private String projectId;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 执行环境 ID */
    private String environmentId;

    /** 任务级执行参数 */
    private Map<String, Object> variables;

    /** 执行机完整地址 */
    private String executorUrl;

    /** 执行机域名 / IP */
    private String executorHost;

    /** 执行机端口 */
    private Integer executorPort;

    /** 失败重试次数 */
    private Integer retryCount;

    /** 超时时间（毫秒） */
    private Integer timeoutMs;

    /** 引用的 UI 用例步骤列表 */
    private List<Map<String, Object>> steps;
}
