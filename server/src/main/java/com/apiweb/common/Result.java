package com.apiweb.common;

import lombok.Data;

/**
 * 统一 RESTful 响应格式。
 *
 * <p>所有 Controller 返回值都包装为 {@code Result<T>}，前端 axios interceptor 自动解包 data 字段。
 *
 * <h3>响应结构</h3>
 * <pre>
 * {
 *   "code": 0,            // 业务状态码：0=成功，其它=失败
 *   "message": "ok",      // 提示信息（成功为 "ok"，失败为错误描述）
 *   "data": { ... }       // 业务数据，类型由泛型 T 决定
 * }
 * </pre>
 *
 * <h3>HTTP 状态码 vs 业务码</h3>
 * <ul>
 *   <li>HTTP 状态码：用于传输层（成功 200 / 业务失败 400 / 未授权 401 / 权限不足 403 / 服务异常 500）</li>
 *   <li>业务码 code：用于业务语义细分，由 {@link ErrorCode} 定义</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   &#64;GetMapping("/{id}")
 *   public Result&lt;UserEntity&gt; get(&#64;PathVariable String id) {
 *       UserEntity u = userService.get(id);
 *       return Result.ok(u);
 *   }
 *
 *   &#64;PostMapping
 *   public Result&lt;Void&gt; create(&#64;RequestBody UserEntity u) {
 *       userService.create(u);
 *       return Result.ok();
 *   }
 * </pre>
 */
@Data
public class Result<T> {
    /** 业务状态码：{@code 0}=成功，其它值参考 {@link ErrorCode} */
    private int code;
    /** 提示信息：成功为 "ok"，失败时为错误描述 */
    private String message;
    /** 业务数据：列表 / 对象 / 基本类型，失败时为 null */
    private T data;

    /**
     * 成功响应（带数据）。
     *
     * @param data 业务数据
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    /**
     * 成功响应（无数据）。
     */
    public static Result<Void> ok() {
        return ok(null);
    }

    /**
     * 失败响应。
     *
     * @param code    业务码（见 {@link ErrorCode}）
     * @param message 错误描述（前端会直接展示给用户）
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
