package com.apiweb.observability;

/**
 * 请求级 TraceId 上下文（基于 ThreadLocal）。
 *
 * <p>与 {@code org.slf4j.MDC} 配套使用：{@link TraceIdFilter} 在请求开始时生成/透传
 * traceId，同时写入 MDC（供日志 {@code %X{traceId}} 打印）与本 ThreadLocal（供业务代码
 * 或跨线程传递时显式获取）。两者在请求结束后一并清理，避免线程复用导致串号或内存泄漏。
 *
 * <h3>与 UserContext 的区别</h3>
 * <ul>
 *   <li>{@code UserContext}：业务身份（当前登录用户），由 {@code AuthInterceptor} 写入</li>
 *   <li>{@code TraceContext}：链路追踪标识（traceId），由 {@code TraceIdFilter} 写入，
 *       不依赖登录态，白名单路径同样生效</li>
 * </ul>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>异步线程（{@code @Async} / {@code @RabbitListener} / Quartz Job）中本 ThreadLocal 为空，
 *       需要显式从消息/参数中取 traceId 后 {@link #set(String)} 注入</li>
 *   <li>绝不要在过滤器之前（如 Servlet Listener）访问——那时 traceId 尚未生成</li>
 * </ul>
 */
public final class TraceContext {

    /** 持有当前线程 traceId 的 ThreadLocal */
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {}

    /**
     * 设置当前线程的 traceId（由 TraceIdFilter 或跨线程任务显式调用）。
     *
     * @param traceId 链路追踪标识，不可为空
     */
    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    /**
     * 获取当前线程的 traceId，未设置时返回 null。
     *
     * @return traceId，可能为 null
     */
    public static String get() {
        return HOLDER.get();
    }

    /**
     * 清理 ThreadLocal（由 TraceIdFilter.afterCompletion 调用，防止内存泄漏）。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
