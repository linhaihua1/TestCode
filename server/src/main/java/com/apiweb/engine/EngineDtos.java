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
 *
 * <p>本类集中存放执行引擎（HTTP / UI / 性能）共用的数据结构，按职责划分为 4 个层次：
 * <ol>
 *   <li>步骤定义层：{@link KV} / {@link Assertion} / {@link Extract} / {@link HttpStep} / {@link ControlStep}</li>
 *   <li>执行结果层：{@link AssertionResult} / {@link ExtractResult} / {@link StepResult} / {@link ExecutionResult}</li>
 *   <li>异步任务消息层：{@link TaskMessage}</li>
 * </ol>
 *
 * <p>所有 DTO 使用 Lombok 自动生成 getter/setter/toString，{@code builder} 注解使得复杂结果对象可链式构造。
 */
public final class EngineDtos {
    private EngineDtos() {}

    // ====================================================================
    // 步骤定义层：从前端 / 数据库传入，描述"做什么"
    // ====================================================================

    /**
     * 通用键值对。用于描述 HTTP Headers、Query 参数、用户变量等场景。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KV {
        /** 参数名（如 "Content-Type"） */
        private String key;
        /** 参数值（如 "application/json"），支持 {{var}} 占位符 */
        private String value;
        /** 是否启用：false 时该参数会被忽略（默认 true） */
        private Boolean enabled;
    }

    /**
     * 断言定义：执行完成后对响应做规则校验。
     *
     * <p>支持 5 种数据源 + 9 种运算符的组合，详见各字段注释。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assertion {
        /**
         * 数据源：
         * <ul>
         *   <li>status：响应状态码（property 留空）</li>
         *   <li>header：响应 Header（property = header 名）</li>
         *   <li>body：响应原始 body（property 留空）</li>
         *   <li>jsonpath：JSONPath 表达式取出的值（property = JSONPath 路径，如 $.data.id）</li>
         *   <li>responsetime：响应耗时 ms（property 留空）</li>
         * </ul>
         */
        private String source;
        /** 字段名 / JSONPath / Header 名（按 source 类型决定） */
        private String property;
        /**
         * 比较运算符：
         * <ul>
         *   <li>equals / not_equals：相等 / 不等</li>
         *   <li>contains / not_contains：包含 / 不包含</li>
         *   <li>regex：正则匹配</li>
         *   <li>gt / lt / gte / lte：数值比较</li>
         *   <li>empty / not_empty：判空</li>
         * </ul>
         */
        private String operator;
        /** 期望值（字符串；gt/lt 等时会被解析为 double） */
        private String expected;
    }

    /**
     * 提取定义：把响应中的某个值保存为变量供后续步骤使用。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Extract {
        /** 提取方式：jsonpath（JSONPath） / regex（正则） / header（按 Header 名） */
        private String type;
        /** 表达式（JSONPath 路径或正则表达式） */
        private String expression;
        /** 保存到的变量名，后续用 {{变量名}} 引用 */
        private String variable;
    }

    /**
     * 一个 HTTP 请求步骤的完整定义。
     *
     * <p>对应前端工作台"用例编辑"中的一个 step。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HttpStep {
        /** 步骤名称（用于报告显示） */
        private String name;
        /** HTTP 方法：GET / POST / PUT / DELETE / PATCH / HEAD / OPTIONS */
        private String method;
        /** 完整 URL，支持 {{var}} 占位符 */
        private String url;
        /** 请求头列表 */
        private List<KV> headers;
        /** Query 参数列表（会自动拼接到 URL） */
        private List<KV> query;
        /** 请求体（POST/PUT 时使用） */
        private String body;
        /** 断言列表 */
        private List<Assertion> assertions;
        /** 提取列表 */
        private List<Extract> extracts;
    }

    /**
     * 控制器步骤：实现 IF / FOR / WHILE / SCRIPT 四种流程控制。
     *
     * <p>使用嵌套的 children 表示分支或循环体（树形结构）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ControlStep {
        /** 类型：if（条件分支）/ for（计数循环）/ while（条件循环）/ script（脚本执行） */
        private String type;
        /**
         * 条件表达式：
         * <ul>
         *   <li>IF：JS 表达式，true 走 children[0]，false 走 children[1]</li>
         *   <li>WHILE：JS 表达式，true 重复执行 children[0]，最大时长由 timeoutMs 控制</li>
         * </ul>
         */
        private String condition;
        /** FOR 循环次数 */
        private Integer loopCount;
        /** WHILE 循环最大超时（毫秒），超过后强制退出 */
        private Long timeoutMs;
        /** SCRIPT 步骤要执行的脚本内容 */
        private String script;
        /** 脚本语言：javascript（默认）/ groovy（待实现） */
        private String language;
        /** 子步骤列表（IF 分支 / FOR/WHILE 循环体） */
        private List<Map<String, Object>> children;
    }

    // ====================================================================
    // 执行结果层：执行引擎回写到数据库 / 返回给前端的结构
    // ====================================================================

    /**
     * 单条断言的执行结果。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssertionResult {
        private String source;
        private String property;
        private String operator;
        private String expected;
        /** 实际值（用于报告展示） */
        private String actual;
        /** true=通过，false=失败 */
        private boolean passed;
        /** 失败时的错误描述 */
        private String message;
    }

    /**
     * 一次提取的结果。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractResult {
        private String variable;
        /** 提取到的值（字符串形式；复杂结构会序列化为 JSON） */
        private String value;
    }

    /**
     * 单个步骤的执行结果。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResult {
        /** 步骤名（来自 HttpStep.name 或 ControlStep.type） */
        private String stepName;
        /** 状态：success / failed / skipped / error */
        private String status;
        /** 本步骤耗时（毫秒） */
        private long durationMs;
        /** 异常信息（如有） */
        private String error;
        private List<AssertionResult> assertions;
        private List<ExtractResult> extracts;
        /** 请求摘要（截断到 1KB，超过则引用 OSS） */
        private String requestSummary;
        /** 响应摘要（截断到 1KB，超过则引用 OSS） */
        private String responseSummary;
    }

    /**
     * 整体执行结果（一个用例 / 场景的全部步骤汇总）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionResult {
        /** success / failed / error（任何步骤 error 都视为整体 error） */
        private String result;
        /** 总耗时（含步骤间等待） */
        private long totalDurationMs;
        @Builder.Default
        private List<StepResult> steps = new ArrayList<>();
        /** 执行结束时的变量快照（用于报告展示"哪些变量被使用"） */
        @Builder.Default
        private Map<String, Object> variables = new LinkedHashMap<>();
        /** 整体错误日志（步骤异常的汇总） */
        private String errorLog;
    }

    // ====================================================================
    // 异步任务消息层：RabbitMQ 队列传输的载体
    // ====================================================================

    /**
     * RabbitMQ 任务消息：Controller 投递到队列，消费者读取后异步执行。
     *
     * <p>三类任务（api / ui / perf）共享同一个消息结构，按 {@link #taskType} 路由到不同的 Consumer。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskMessage {
        /** 任务类型：api（接口自动化）/ ui（UI 自动化）/ perf（性能测试） */
        private String taskType;
        /**
         * 运行记录 id：
         * <ul>
         *   <li>api：对应 t_test_task_run.id</li>
         *   <li>ui：对应 t_ui_report.id</li>
         *   <li>perf：对应 t_perf_report.id</li>
         * </ul>
         */
        private String runId;
        private String projectId;
        /** 用例 id（接口/UI/性能 用例共享 ID 空间但表不同） */
        private String caseId;
        /** 任务 id（t_test_task.id），仅 api 任务有 */
        private String taskId;
        /** 执行环境 id（从环境表取 host/headers/variables） */
        private String environmentId;
        /** 任务扩展参数（如 UI 浏览器类型、性能压测目标并发数） */
        private Map<String, Object> extra;
    }
}
