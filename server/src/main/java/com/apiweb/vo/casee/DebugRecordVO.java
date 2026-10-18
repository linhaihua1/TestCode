package com.apiweb.vo.casee;

import lombok.Data;

import java.time.Instant;

/**
 * 调试记录 VO（响应）。
 *
 * <p>实体中 assertionResults/extractedVariables/stepResults 以 JSON 字符串存储，
 * 本 VO 对外输出结构化对象，由 {@code CaseConverter#parseDebugRecordJsonFields} 解析。
 * 前端调试记录面板直接按数组/对象使用，输出字符串会导致逐字符渲染。</p>
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
    /** 断言结果：[{source, operator, expected, actual, passed}] */
    private Object assertionResults;
    /** 提取到的变量：{varName: value} */
    private Object extractedVariables;
    /** 步骤执行明细：[{stepName, status, durationMs, ...}] */
    private Object stepResults;
    private String requestBodyRef;
    private String responseBodyRef;
    private String errorLog;
    private String createdBy;
    private Instant createdAt;
}
