package com.apiweb.common;

import lombok.Getter;

/**
 * 业务异常，由全局异常处理器统一转 Result。
 */
@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(ErrorCode.UNAUTHORIZED, message);
    }
}
