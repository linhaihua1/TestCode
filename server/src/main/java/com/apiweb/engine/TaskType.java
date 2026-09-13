package com.apiweb.engine;

/**
 * 任务类型枚举（取代散落各处的 "api" / "ui" / "perf" 字面量）。
 *
 * <p>用途：
 * <ul>
 *   <li>{@link EngineDtos.TaskMessage#getTaskType()} 的取值</li>
 *   <li>按类型路由到对应 MQ 队列 / 路由键（见 {@code MqProperties}）</li>
 *   <li>执行机能力标签（{@code t_executor_node.capabilities}）</li>
 * </ul>
 */
public enum TaskType {

    /** 接口自动化 */
    API("api"),
    /** UI 自动化 */
    UI("ui"),
    /** 性能测试 */
    PERF("perf");

    /** 落库 / 传输用的字符串编码（小写） */
    private final String code;

    TaskType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 兼容解析：大小写不敏感，无法识别时返回 null（调用方自行兜底）。
     */
    public static TaskType parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (TaskType t : values()) {
            if (t.code.equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
