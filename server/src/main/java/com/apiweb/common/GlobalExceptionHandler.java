package com.apiweb.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>Spring MVC 默认会把未捕获异常转为 500 + 空白页面；
 * 通过 {@link RestControllerAdvice} 统一拦截后转为 {@link Result} JSON 响应。
 *
 * <h3>处理三类异常</h3>
 * <ol>
 *   <li>{@link BizException}：业务异常 → 用业务码 + 用户可见 message</li>
 *   <li>{@link MethodArgumentNotValidException}：{@code @Valid} 校验失败 → 取首个字段错误</li>
 *   <li>{@link Exception}：其它未预料异常 → 500 + 通用错误信息 + 服务端日志</li>
 * </ol>
 *
 * <h3>不处理的异常</h3>
 * <ul>
 *   <li>Spring Security / 拦截器自己返回的 401：本类不接管</li>
 *   <li>登录失败（用户名密码错）：由 {@code AuthController} 显式抛 BizException</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理 {@code @Valid} 校验失败（如 {@code @NotBlank @RequestBody}）。
     *
     * <p>返回首个字段错误（如 {@code "username: 用户名不能为空"}），便于前端定位。</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + ": " + fe.getDefaultMessage();
        return Result.fail(ErrorCode.BAD_REQUEST, msg);
    }

    /**
     * 兜底处理：未预料的异常。
     *
     * <p>详细堆栈写入服务端日志（{@code log.error}），前端只看到通用错误信息，
     * 避免泄露内部细节。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误: " + e.getMessage());
    }
}
