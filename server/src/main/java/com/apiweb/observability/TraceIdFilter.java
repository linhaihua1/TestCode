package com.apiweb.observability;

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
 * 链路追踪过滤器：为每个 HTTP 请求生成/透传 traceId，并记录访问日志。
 *
 * <p>这是请求处理链路的<b>第一个</b>过滤器（{@code @Order(HIGHEST_PRECEDENCE)}），
 * 保证任何后续组件（认证、审计、业务）的日志都能带上同一个 traceId，实现全链路追踪。
 *
 * <h3>traceId 生成规则</h3>
 * <ol>
 *   <li>请求头已带 {@code X-Trace-Id}（如网关/上游传入）→ 透传，保证跨服务链路连续</li>
 *   <li>否则生成 32 位 UUID（去掉连字符）作为本请求 traceId</li>
 *   <li>无论哪种来源，统一写回响应头 {@code X-Trace-Id}，方便前端/调用方定位问题</li>
 * </ol>
 *
 * <h3>MDC 与清理</h3>
 * traceId 写入 {@link MDC}（键 {@code traceId}），log4j2 通过 {@code %X{traceId}} 打印。
 * 请求结束（无论成功失败）在 {@code finally} 中清理 MDC 与 {@link TraceContext}，
 * 避免 Tomcat 线程复用导致日志串号。
 *
 * <h3>访问日志</h3>
 * 请求结束时记录一条 INFO 日志：方法、URI、耗时、状态码，便于排查慢请求与异常请求。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** traceId 在 MDC 中的键名（log4j2.xml 用 %X{traceId} 引用） */
    public static final String MDC_KEY = "traceId";

    /** 透传/回写 traceId 的请求头名 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 过滤器核心逻辑：生成/透传 traceId → 放行 → 记录访问日志 → 清理上下文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();

        // 1. 透传上游 traceId，否则生成新 traceId
        String traceId = request.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 2. 写入 MDC 与 ThreadLocal，回写响应头
        MDC.put(MDC_KEY, traceId);
        TraceContext.set(traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);

        // 3. 包装请求/响应，便于访问日志读取 body 大小（仅包装，不缓存大 body）
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
            // 4. 记录访问日志（含耗时与状态码）
            long cost = System.currentTimeMillis() - start;
            int status = response.getStatus();
            log.info("访问日志 method={} uri={} status={} cost={}ms",
                    request.getMethod(), request.getRequestURI(), status, cost);

            // 5. 释放缓存（若使用了 ContentCachingResponseWrapper，需把缓存的响应体刷回客户端）
            if (responseToUse instanceof ContentCachingResponseWrapper wrapper) {
                try {
                    wrapper.copyBodyToResponse();
                } catch (IOException e) {
                    log.warn("复制响应体失败: {}", e.getMessage());
                }
            }

            // 6. 清理上下文，防止线程复用串号
            MDC.remove(MDC_KEY);
            TraceContext.clear();
        }
    }
}
