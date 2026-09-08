package com.apiweb.common;

/**
 * 平台业务错误码常量（对齐开发文档 §6）。
 *
 * <p>前端 axios interceptor 根据 code 做兜底处理：
 * <ul>
 *   <li>0：成功</li>
 *   <li>1001 / 1002 / 1003 / 1004 / 1005：参数 / 未登录 / 无权限 / 不存在 / 名称冲突</li>
 *   <li>2001~2006：业务校验错误（变量 / 用例 / 任务 / 循环）</li>
 *   <li>3001~3004：导入错误（Swagger / Excel / 请求方式 / URL）</li>
 *   <li>4001~4004：执行错误（调试 / 任务 / Mock / 报告）</li>
 *   <li>500：其它未处理异常</li>
 * </ul>
 *
 * <p>具体语义见每个常量的注释。所有自定义业务码必须在 1000~4999 之间。
 */
public final class ErrorCode {
    /** 成功 */
    public static final int SUCCESS = 0;

    // -------- 通用（1001~1005） --------
    /** 请求参数错误（{@code @Valid} 校验失败、业务校验失败） */
    public static final int BAD_REQUEST = 1001;
    /** 未登录或登录已过期 */
    public static final int UNAUTHORIZED = 1002;
    /** 权限不足（非管理员访问受限资源） */
    public static final int FORBIDDEN = 1003;
    /** 资源不存在 */
    public static final int NOT_FOUND = 1004;
    /** 资源名称冲突（同名已存在） */
    public static final int CONFLICT = 1005;

    // -------- 业务校验（2001~2006） --------
    /** 变量名不合法 */
    public static final int INVALID_VARIABLE_NAME = 2001;
    /** 变量被引用，无法删除 */
    public static final int VARIABLE_REFERENCED = 2002;
    /** 用例名称为空 */
    public static final int CASE_NAME_EMPTY = 2003;
    /** 任务名称为空 */
    public static final int TASK_NAME_EMPTY = 2004;
    /** 任务至少需要 1 条用例才能运行 */
    public static final int TASK_EMPTY_CASES = 2005;
    /** 循环控制器未配置最大循环次数 */
    public static final int LOOP_MAX_REQUIRED = 2006;

    // -------- 导入校验（3001~3004） --------
    /** Swagger 文档解析失败 */
    public static final int SWAGGER_PARSE_FAILED = 3001;
    /** Excel 导入失败，存在非法行 */
    public static final int EXCEL_IMPORT_FAILED = 3002;
    /** 请求方式不合法 */
    public static final int INVALID_HTTP_METHOD = 3003;
    /** URL 格式不合法 */
    public static final int INVALID_URL = 3004;

    // -------- 执行错误（4001~4004） --------
    /** 调试执行异常 */
    public static final int DEBUG_EXECUTION_FAILED = 4001;
    /** 任务执行异常 */
    public static final int TASK_EXECUTION_FAILED = 4002;
    /** Mock 服务异常 */
    public static final int MOCK_FAILED = 4003;
    /** 报告生成失败 */
    public static final int REPORT_GENERATE_FAILED = 4004;

    // -------- 其它 --------
    /** 其它未处理异常（兜底） */
    public static final int INTERNAL_ERROR = 500;

    private ErrorCode() {}
}
