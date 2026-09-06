package com.apiweb.common;

/**
 * 平台错误码约定。
 */
public final class ErrorCode {
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int INTERNAL_ERROR = 500;

    private ErrorCode() {}
}
