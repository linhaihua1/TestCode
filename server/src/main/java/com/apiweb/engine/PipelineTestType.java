package com.apiweb.engine;

/**
 * CI/CD 流水线支持的测试类型（取代 "api" / "ui" / "perf" 字面量）。
 *
 * <p>对应 {@code t_ci_pipeline.test_type}，决定流水线执行时调用哪套执行引擎：
 * <ul>
 *   <li>{@link #API} —— 接口自动化：走 {@code TaskExecutorService} 执行测试任务（t_test_task）</li>
 *   <li>{@link #UI} —— UI 自动化：走 {@code UiExecutionService} 执行 UI 场景或 UI 用例</li>
 *   <li>{@link #PERF} —— 性能测试：走 {@code PerfExecutionService} 执行性能用例（内嵌 JMeter）</li>
 * </ul>
 */
public enum PipelineTestType {

    /** 接口自动化 */
    API("api", "接口自动化"),
    /** UI 自动化 */
    UI("ui", "UI 自动化"),
    /** 性能测试 */
    PERF("perf", "性能测试");

    private final String code;
    private final String label;

    PipelineTestType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 前后端 / DB 契约值 */
    public String getCode() {
        return code;
    }

    /** 中文展示名 */
    public String getLabel() {
        return label;
    }

    /** 大小写不敏感解析，无法识别返回 null */
    public static PipelineTestType parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (PipelineTestType v : values()) {
            if (v.code.equalsIgnoreCase(s)) return v;
        }
        return null;
    }

    /** 解析，无法识别返回 {@link #API} */
    public static PipelineTestType parseOrDefault(String s) {
        PipelineTestType v = parse(s);
        return v == null ? API : v;
    }
}
