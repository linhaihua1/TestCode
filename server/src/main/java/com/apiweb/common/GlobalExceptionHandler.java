package com.apiweb.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
 *   <li>参数绑定类异常（必填参数缺失、路径变量缺失、类型不匹配、请求体不可解析）→ 400 并指出具体参数</li>
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
     * 处理 {@code @RequestParam} 必填参数缺失。
     *
     * <p>若不单独处理，会被兜底分支转成 500「服务器内部错误」，
     * 让调用方误以为服务端故障；实际这是客户端请求问题，应返回 400 并指出缺哪个参数。</p>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.BAD_REQUEST, "缺少必填参数: " + e.getParameterName());
    }

    /**
     * 处理路径变量缺失（如 {@code /cases/{id}} 未传 id）。
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public Result<Void> handleMissingPathVar(MissingPathVariableException e) {
        return Result.fail(ErrorCode.BAD_REQUEST, "缺少路径参数: " + e.getVariableName());
    }

    /**
     * 处理参数类型不匹配（如把 version 传成非数字、把布尔值传成字符串）。
     *
     * <p>提示中带上参数名与期望类型，方便调用方自查。</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expect = e.getRequiredType() == null ? "" : e.getRequiredType().getSimpleName();
        return Result.fail(ErrorCode.BAD_REQUEST,
                "参数类型不匹配: " + e.getName() + "，期望 " + expect + "，实际 " + e.getValue());
    }

    /**
     * 处理请求体不可解析（JSON 语法错误、字段类型写错等）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        return Result.fail(ErrorCode.BAD_REQUEST, "请求体格式错误，无法解析为 JSON");
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
