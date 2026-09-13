package com.apiweb.engine;

/**
 * CI/CD 流水线的执行方式（取代 "local" / "jenkins" 字面量）。
 *
 * <p>对应 {@code t_ci_pipeline.exec_mode}：
 * <ul>
 *   <li>{@link #LOCAL} —— 平台本地执行（本服务直接跑用例，结果写入平台报告）</li>
 *   <li>{@link #JENKINS} —— 平台调用 Jenkins Remote API 触发远端 Job，由 Jenkins 侧
 *       执行（典型用法：Jenkins Job 里再回调平台 openapi 触发用例，或执行外部脚本）</li>
 * </ul>
 */
public enum PipelineExecMode {

    /** 平台本地执行 */
    LOCAL("local", "平台执行"),
    /** 调用 Jenkins Job 执行 */
    JENKINS("jenkins", "Jenkins 执行");

    private final String code;
    private final String label;

    PipelineExecMode(String code, String label) {
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
    public static PipelineExecMode parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (PipelineExecMode v : values()) {
            if (v.code.equalsIgnoreCase(s)) return v;
        }
        return null;
    }

    /** 解析，无法识别返回 {@link #LOCAL} */
    public static PipelineExecMode parseOrDefault(String s) {
        PipelineExecMode v = parse(s);
        return v == null ? LOCAL : v;
    }
}
