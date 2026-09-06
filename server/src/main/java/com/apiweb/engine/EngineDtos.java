package com.apiweb.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行引擎通用 DTO 集合。
 */
public final class EngineDtos {
    private EngineDtos() {}

    /** 键值对（headers/query/variables） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KV {
        private String key;
        private String value;
        private Boolean enabled;
    }

    /** 断言定义 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assertion {
        /** source: status / header / body / jsonpath / responsetime */
        private String source;
        private String property;
        /** equals / not_equals / contains / not_contains / regex / gt / lt / gte / lte / empty / not_empty */
        private String operator;
        private String expected;
    }

    /** 提取定义 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Extract {
        /** jsonpath / regex / header */
        private String type;
        private String expression;
        private String variable;
    }

    /** 一个 HTTP 请求步骤的定义 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HttpStep {
        private String name;
        private String method;
        private String url;
        private List<KV> headers;
        private List<KV> query;
        private String body;
        private List<Assertion> assertions;
        private List<Extract> extracts;
    }

    /** 控制器步骤：IF / FOR / WHILE / SCRIPT */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ControlStep {
        /** if / for / while / script */
        private String type;
        /** IF: JS 表达式；FOR: 次数；WHILE: JS 表达式（带超时） */
        private String condition;
        private Integer loopCount;
        private Long timeoutMs;
        /** SCRIPT: javascript */
        private String script;
        private String language;
        /** 子步骤 */
        private List<Map<String, Object>> children;
    }

    /** 断言执行结果 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssertionResult {
        private String source;
        private String property;
        private String operator;
        private String expected;
        private String actual;
        private boolean passed;
        private String message;
    }

    /** 提取执行结果 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractResult {
        private String variable;
        private String value;
    }

    /** 单步执行结果 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResult {
        private String stepName;
        private String status;
        private long durationMs;
        private String error;
        private List<AssertionResult> assertions;
        private List<ExtractResult> extracts;
        private String requestSummary;
        private String responseSummary;
    }

    /** 整体执行结果 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionResult {
        private String result;
        private long totalDurationMs;
        @Builder.Default
        private List<StepResult> steps = new ArrayList<>();
        @Builder.Default
        private Map<String, Object> variables = new LinkedHashMap<>();
        private String errorLog;
    }

    /** MQ 任务消息（RabbitMQ 异步投递） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskMessage {
        /** api / ui / perf */
        private String taskType;
        /** runId：TestTaskRun / UiReport / PerfReport 的 id */
        private String runId;
        private String projectId;
        private String caseId;
        private String taskId;
        private String environmentId;
        private Map<String, Object> extra;
    }
}
