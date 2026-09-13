package com.apiweb.engine;

/**
 * CI/CD 流水线的执行目标类型（取代 "test_task" / "ui_scenario" 等字面量）。
 *
 * <p>对应 {@code t_ci_pipeline.target_type}。目标类型与 {@link PipelineTestType}
 * 的对应关系（前端按测试类型过滤可选目标）：
 * <ul>
 *   <li>{@link PipelineTestType#API} → {@link #TEST_TASK}</li>
 *   <li>{@link PipelineTestType#UI} → {@link #UI_SCENARIO} 或 {@link #UI_TEST}</li>
 *   <li>{@link PipelineTestType#PERF} → {@link #PERF_CASE}</li>
 * </ul>
 */
public enum PipelineTargetType {

    /** 接口测试任务（t_test_task） */
    TEST_TASK("test_task", "测试任务"),
    /** UI 场景（t_ui_scenario） */
    UI_SCENARIO("ui_scenario", "UI 场景"),
    /** UI 用例（t_ui_test_case） */
    UI_TEST("ui_test", "UI 用例"),
    /** 性能用例（t_perf_case） */
    PERF_CASE("perf_case", "性能用例");

    private final String code;
    private final String label;

    PipelineTargetType(String code, String label) {
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
    public static PipelineTargetType parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (PipelineTargetType v : values()) {
            if (v.code.equalsIgnoreCase(s)) return v;
        }
        return null;
    }

    /** 解析，无法识别返回 {@link #TEST_TASK} */
    public static PipelineTargetType parseOrDefault(String s) {
        PipelineTargetType v = parse(s);
        return v == null ? TEST_TASK : v;
    }

    /** 按测试类型给出默认目标类型 */
    public static PipelineTargetType defaultOf(PipelineTestType type) {
        if (type == null) return TEST_TASK;
        return switch (type) {
            case API -> TEST_TASK;
            case UI -> UI_SCENARIO;
            case PERF -> PERF_CASE;
        };
    }
}
