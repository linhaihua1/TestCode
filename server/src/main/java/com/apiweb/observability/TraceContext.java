package com.apiweb.observability;

/**
 * 请求级 TraceId 上下文（基于 ThreadLocal）。
 *
 * <p>接入 OpenTelemetry（Micrometer Tracing）后，HTTP 请求的 traceId 由 OTel 自动生成
 * 并写入 MDC（供日志 {@code %X{traceId}} 打印）。本类作为<b>跨线程场景的轻量辅助</b>，
 * 供无法使用 {@code Observation} / {@code Tracer} 上下文传播的边界场景（如 {@code @Async}
 * 线程池、Quartz Job、{@code @RabbitListener} 消费者）显式传递 traceId。
 *
 * <h3>典型用法（异步任务显式透传）</h3>
 * <pre>
 *   // 主线程：取出当前 traceId
 *   String traceId = tracer.currentSpan().context().traceId();
 *   // 子线程：注入
 *   TraceContext.set(traceId);
 *   MDC.put("traceId", traceId);
 *   try { ... } finally { TraceContext.clear(); MDC.remove("traceId"); }
 * </pre>
 *
 * <h3>与 UserContext 的区别</h3>
 * <ul>
 *   <li>{@code UserContext}：业务身份（当前登录用户），由 {@code AuthInterceptor} 写入</li>
 *   <li>{@code TraceContext}：链路追踪标识（traceId）的跨线程透传辅助，不依赖登录态</li>
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
