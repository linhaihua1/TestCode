package com.apiweb.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>与系统异常（{@link RuntimeException}）区分，业务异常是"用户操作导致的可预期错误"，
 * 由 {@link GlobalExceptionHandler} 统一捕获并转换为 {@link Result} 返回前端。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>参数校验失败：{@code throw BizException.badRequest("用户名不能为空")}</li>
 *   <li>资源不存在：{@code throw BizException.notFound("用例不存在: " + id)}</li>
 *   <li>权限不足：{@code throw BizException.forbidden("仅管理员可访问")}</li>
 *   <li>业务校验：{@code throw BizException.of(ErrorCode.VARIABLE_REFERENCED, "变量被 5 处引用")}</li>
 * </ul>
 *
 * <h3>与 Spring Security 异常的区别</h3>
 * 本异常用于业务校验；鉴权 / 授权失败由 {@link com.apiweb.security.AuthInterceptor} 返回 1002，
 * 不会经过本异常的捕获流程。
 */
@Getter
public class BizException extends RuntimeException {
    /** 业务错误码（见 {@link ErrorCode}） */
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 通用工厂方法（推荐用此方法配合 {@link ErrorCode} 常量）。
     */
    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }

    /**
     * 资源不存在（1004）。
     */
    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    /**
     * 请求参数错误（1001）。
     */
    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 权限不足（1003）。
     */
    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }

    /**
     * 未登录（1002），实际登录失败由拦截器直接返回，不会到这里。
     */
    public static BizException unauthorized(String message) {
        return new BizException(ErrorCode.UNAUTHORIZED, message);
    }

    /**
     * 资源冲突（1005）。
     */
    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    // -------- 业务校验便捷工厂（开发文档 §6 错误码） --------

    /** 变量名不合法（2001） */
    public static BizException invalidVariableName(String message) {
        return new BizException(ErrorCode.INVALID_VARIABLE_NAME, message);
    }

    /** 变量被引用，无法删除（2002） */
    public static BizException variableReferenced(String message) {
        return new BizException(ErrorCode.VARIABLE_REFERENCED, message);
    }

    /** 用例名称为空（2003） */
    public static BizException caseNameEmpty() {
        return new BizException(ErrorCode.CASE_NAME_EMPTY, "用例名称不能为空");
    }

    /** 任务名称为空（2004） */
    public static BizException taskNameEmpty() {
        return new BizException(ErrorCode.TASK_NAME_EMPTY, "任务名称不能为空");
    }

    /** 任务至少需要 1 条用例才能运行（2005） */
    public static BizException taskEmptyCases() {
        return new BizException(ErrorCode.TASK_EMPTY_CASES, "任务至少需要 1 条用例才能运行");
    }

    /** 循环控制器未配置最大循环次数（2006） */
    public static BizException loopMaxRequired() {
        return new BizException(ErrorCode.LOOP_MAX_REQUIRED, "WHILE / FOR 循环必须配置最大循环次数");
    }
}
