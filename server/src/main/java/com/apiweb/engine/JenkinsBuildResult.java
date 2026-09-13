package com.apiweb.engine;

/**
 * Jenkins 构建结果（Jenkins Remote API 返回的 {@code result} 字段，可能为 null 表示构建中）。
 *
 * <p>用于把 Jenkins 的构建结果映射为平台的 {@link PipelineRunStatus}，
 * 避免代码中出现 "SUCCESS" / "UNSTABLE" 等字面量。
 */
public enum JenkinsBuildResult {

    /** 构建成功 */
    SUCCESS("SUCCESS"),
    /** 构建失败 */
    FAILURE("FAILURE"),
    /** 构建成功但不稳定（如测试有失败用例） */
    UNSTABLE("UNSTABLE"),
    /** 构建被中止 */
    ABORTED("ABORTED"),
    /** 构建尚未结束（result 为 null 且 building=true） */
    RUNNING("RUNNING"),
    /** 构建不存在（队列已过期或编号错误） */
    NOT_FOUND("NOT_FOUND"),
    /** 未知 / 无法解析 */
    UNKNOWN("UNKNOWN");

    private final String code;

    JenkinsBuildResult(String code) {
        this.code = code;
    }

    /** Jenkins 侧原始值 */
    public String getCode() {
        return code;
    }

    /** 是否构建结束（终态） */
    public boolean isFinished() {
        return this == SUCCESS || this == FAILURE || this == UNSTABLE || this == ABORTED;
    }

    /**
     * 映射为平台流水线执行状态。
     *
     * @return 对应 {@link PipelineRunStatus}；构建中返回 {@code RUNNING}
     */
    public PipelineRunStatus toRunStatus() {
        return switch (this) {
            case SUCCESS -> PipelineRunStatus.SUCCESS;
            case FAILURE, UNSTABLE, ABORTED -> PipelineRunStatus.FAILED;
            case RUNNING -> PipelineRunStatus.RUNNING;
            case NOT_FOUND -> PipelineRunStatus.ERROR;
            default -> PipelineRunStatus.ERROR;
        };
    }

    /** 大小写不敏感解析，无法识别（含 null）返回 {@link #UNKNOWN} */
    public static JenkinsBuildResult parse(String s) {
        if (s == null || s.isBlank()) return RUNNING;
        for (JenkinsBuildResult v : values()) {
            if (v.code.equalsIgnoreCase(s)) return v;
        }
        return UNKNOWN;
    }
}
