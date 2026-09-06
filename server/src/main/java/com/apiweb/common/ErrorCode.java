package com.apiweb.common;

/**
 * 平台业务错误码常量。
 *
 * <p>前端 axios interceptor 根据 code 做兜底处理：
 * <ul>
 *   <li>401：跳转到登录页</li>
 *   <li>403：提示"权限不足"</li>
 *   <li>404：提示"资源不存在"</li>
 *   <li>500：提示"系统异常，请稍后重试"</li>
 * </ul>
 */
public final class ErrorCode {
    /** 请求参数错误（{@code @Valid} 校验失败、业务校验失败） */
    public static final int BAD_REQUEST = 400;
    /** 未登录或登录已过期 */
    public static final int UNAUTHORIZED = 401;
    /** 权限不足（非管理员访问受限资源） */
    public static final int FORBIDDEN = 403;
    /** 资源不存在 */
    public static final int NOT_FOUND = 404;
    /** 服务器内部错误（未处理异常） */
    public static final int INTERNAL_ERROR = 500;

    private ErrorCode() {}
}
