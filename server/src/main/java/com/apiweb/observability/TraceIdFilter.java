package com.apiweb.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器：为每个 HTTP 请求回写 traceId 并记录访问日志。
 *
 * <p>traceId 优先取自 Micrometer Tracing + OpenTelemetry 桥接（Spring Boot 3 官方
 * tracing 抽象，W3C {@code traceparent} 标准）；若当前过滤阶段 OTel span 尚不可见
 * （如未启用 tracing、或过滤器顺序早于 OTel 埋点），则兜底生成 32 位 UUID，
 * 保证 MDC 始终有 traceId、日志链路不中断。职责：
 * <ol>
 *   <li>读取 OTel 标准 traceId，或兜底生成 UUID</li>
 *   <li>写入 MDC（键 {@code traceId}），供 log4j2 {@code %X{traceId}} 打印</li>
 *   <li>回写响应头 {@code X-Trace-Id}，方便前端/调用方定位问题</li>
 *   <li>记录访问日志（方法、URI、状态码、耗时）</li>
 *   <li>请求结束兜底清理 MDC（防止线程复用串号）</li>
 * </ol>
 *
 * <h3>与 OpenTelemetry 的分工</h3>
 * <ul>
 *   <li><b>traceId 生成 / 传播</b>：优先由 OTel 完成（W3C {@code traceparent} 标准），
 *       跨服务链路连续；本过滤器仅在 span 不可见时兜底生成，保证单机日志可追踪</li>
 *   <li><b>MDC 写入</b>：本过滤器显式写入 {@code traceId}，保证不依赖 OTel 的自动关联</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class TraceIdFilter extends OncePerRequestFilter {

    /** traceId 在 MDC 中的键名（log4j2.xml 用 %X{traceId} 引用，与 OTel 自动写入的键一致） */
    public static final String MDC_KEY = "traceId";

    /** 回写 traceId 的请求头名（兼容既有调用方；标准传播走 W3C traceparent） */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** Micrometer Tracing 门面（Spring 注入，OTel 桥接实现） */
    private final Tracer tracer;

    public TraceIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 过滤器核心逻辑：读取/生成 traceId → 放行 → 记录访问日志 → 清理上下文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();

        // 1. 优先取 OTel 标准 traceId（W3C traceparent）；若当前阶段 span 尚不可见则兜底生成，
        //    保证 MDC 始终有 traceId，日志链路不中断
        String traceId = currentTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);

        // 2. 包装请求/响应，便于访问日志读取（仅包装，不缓存大 body）
        boolean shouldWrap = !isAsyncDispatch(request);
        HttpServletRequest requestToUse = request;
        HttpServletResponse responseToUse = response;
        if (shouldWrap && !(request instanceof ContentCachingRequestWrapper)) {
            requestToUse = new ContentCachingRequestWrapper(request);
        }
        if (shouldWrap && !(response instanceof ContentCachingResponseWrapper)) {
            responseToUse = new ContentCachingResponseWrapper(response);
        }

        try {
            chain.doFilter(requestToUse, responseToUse);
        } finally {
            // 3. 记录访问日志（含耗时与状态码，traceId 显式作为参数传入，避免依赖 MDC——
            //    因为 tracing 框架的 span 在 chain 返回后已关闭、可能已清理 MDC 的 traceId 键）
            long cost = System.currentTimeMillis() - start;
            int status = response.getStatus();
            log.info("访问日志 traceId={} method={} uri={} status={} cost={}ms",
                    traceId, request.getMethod(), request.getRequestURI(), status, cost);

            // 4. 释放缓存（若使用了 ContentCachingResponseWrapper，需把缓存的响应体刷回客户端）
            if (responseToUse instanceof ContentCachingResponseWrapper wrapper) {
                try {
                    wrapper.copyBodyToResponse();
                } catch (IOException e) {
                    log.warn("复制响应体失败: {}", e.getMessage());
                }
            }

            // 5. 兜底清理 MDC（防止线程复用串号）
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 从 Micrometer Tracer 读取当前 span 的 traceId。
     *
     * @return 标准 traceId（W3C 格式），无当前 span 或未启用 tracing 时返回 null
     */
    private String currentTraceId() {
        try {
            io.micrometer.tracing.Span span = tracer.currentSpan();
            return span == null ? null : span.context().traceId();
        } catch (Exception e) {
            // tracing 未启用或 span 上下文异常时静默降级
            return null;
        }
    }
}
