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
 * </ul>
 *
 * <h3>与 Spring Security 异常的区别</h3>
 * 本异常用于业务校验；鉴权 / 授权失败由 {@link com.apiweb.security.AuthInterceptor} 返回 401，
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
     * 资源不存在（404 语义）。
     *
     * @param message 错误描述，建议包含具体资源 ID 便于排查
     */
    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    /**
     * 请求参数错误（400 语义）。
     *
     * @param message 错误描述，应直接展示给用户
     */
    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 权限不足（403 语义）。
     */
    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }

    /**
     * 未登录（401 语义），实际登录失败由拦截器直接返回，不会到这里。
     */
    public static BizException unauthorized(String message) {
        return new BizException(ErrorCode.UNAUTHORIZED, message);
    }
}
