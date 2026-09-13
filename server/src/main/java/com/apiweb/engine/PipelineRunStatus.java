package com.apiweb.engine;

/**
 * CI/CD 流水线执行记录状态（取代 "pending" / "running" / "success" / "failed" / "error" 字面量）。
 *
 * <p>对应 {@code t_ci_pipeline_run.status}。Jenkins 侧执行时：触发成功置
 * {@link #RUNNING}，轮询到构建结束再按 Jenkins 结果映射为终态。
 */
public enum PipelineRunStatus {

    /** 已创建，等待调度执行 */
    PENDING("pending", "等待中"),
    /** 执行中（本地执行中，或 Jenkins 构建进行中） */
    RUNNING("running", "执行中"),
    /** 执行成功且通过质量门禁 */
    SUCCESS("success", "通过"),
    /** 执行完成但质量门禁未通过（用例失败或指标超标） */
    FAILED("failed", "未通过"),
    /** 执行过程异常（目标不存在、Jenkins 不可达、执行引擎报错） */
    ERROR("error", "执行异常");

    private final String code;
    private final String label;

    PipelineRunStatus(String code, String label) {
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

    /** 是否终态（不再变化，CI 轮询到此即可结束） */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == ERROR;
    }

    /** 是否为「未通过」状态（门禁失败或执行异常） */
    public boolean isFailure() {
        return this == FAILED || this == ERROR;
    }

    /** 大小写不敏感解析，无法识别返回 null */
    public static PipelineRunStatus parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (PipelineRunStatus v : values()) {
            if (v.code.equalsIgnoreCase(s)) return v;
        }
        return null;
    }
}
