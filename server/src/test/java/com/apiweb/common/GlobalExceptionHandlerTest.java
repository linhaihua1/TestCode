package com.apiweb.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理器契约测试（回归守卫）。
 *
 * <p>背景：运行时冒烟发现「请求不存在的路径 / 用错 HTTP 方法」会落入兜底分支
 * 返回 500「服务器内部错误」，让调用方误判为服务端故障。
 * 修复后必须分别返回 404 / 405，且响应体为统一 Result 结构。</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("不支持的 HTTP 方法 -> 业务码 1001，HTTP 405")
    void methodNotSupportedShouldReturn405() {
        HttpRequestMethodNotSupportedException e =
                new HttpRequestMethodNotSupportedException("GET");
        Result<Void> result = handler.handleMethodNotSupported(e);

        assertThat(result.getCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(result.getMessage()).contains("GET");
        // @ResponseStatus 在运行时由 Spring 解析，此处通过注解断言状态码契约
        ResponseStatus rs;
        try {
            rs = GlobalExceptionHandler.class
                    .getMethod("handleMethodNotSupported", HttpRequestMethodNotSupportedException.class)
                    .getAnnotation(ResponseStatus.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
        assertThat(rs.value()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("请求路径不存在 -> 业务码 1004，HTTP 404")
    void noResourceFoundShouldReturn404() {
        NoResourceFoundException e = new NoResourceFoundException(null, "nonexistent-xxx");
        Result<Void> result = handler.handleNoResource(e);

        assertThat(result.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
        ResponseStatus rs;
        try {
            rs = GlobalExceptionHandler.class
                    .getMethod("handleNoResource", NoResourceFoundException.class)
                    .getAnnotation(ResponseStatus.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
        assertThat(rs.value()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("请求体不可解析 -> 业务码 1001，文案不含内部字段名")
    void unreadableBodyShouldReturn400() {
        Result<Void> result = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad json", new RuntimeException()));

        assertThat(result.getCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(result.getMessage()).isEqualTo("请求体格式错误，无法解析为 JSON");
    }

    @Test
    @DisplayName("兜底异常 -> 业务码 500，且不泄露内部异常信息")
    void unknownExceptionShouldNotLeakInternalDetail() {
        String secret = "jdbc:mysql://10.0.0.1:3306/api_web?user=root";
        Result<Void> result = handler.handleOther(new RuntimeException(secret));

        assertThat(result.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        // 兜底文案必须是通用信息，不能回显异常 message（防止泄露连接串 / 库结构等内部细节）
        assertThat(result.getMessage()).doesNotContain(secret);
        assertThat(result.getMessage()).doesNotContain("jdbc:mysql");
        ResponseStatus rs;
        try {
            rs = GlobalExceptionHandler.class
                    .getMethod("handleOther", Exception.class)
                    .getAnnotation(ResponseStatus.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
        assertThat(rs.value()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
