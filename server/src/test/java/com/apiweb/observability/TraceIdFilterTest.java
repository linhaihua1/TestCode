package com.apiweb.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TraceIdFilter} 链路追踪过滤器测试。
 *
 * <p>接入 OpenTelemetry 后，traceId 由 {@link Tracer} 提供（W3C 标准），
 * 本过滤器只负责回写响应头与记录访问日志。核心契约：
 * <ol>
 *   <li>从当前 span 读取 traceId 并回写响应头 {@code X-Trace-Id}</li>
 *   <li>写入 MDC 供日志打印</li>
 *   <li>无当前 span（tracing 未启用）时静默降级，不回写、不 NPE</li>
 *   <li>请求结束（成功或异常）后 MDC 被清理，防止线程复用串号</li>
 * </ol>
 */
class TraceIdFilterTest {

    private TraceIdFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        filter = new TraceIdFilter(tracer);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        com.apiweb.observability.TraceContext.clear();
    }

    /** 构造一个带指定 traceId 的当前 span mock。 */
    private void stubCurrentSpan(String traceId) {
        Span span = mock(Span.class);
        TraceContext spanContext = mock(TraceContext.class);
        when(span.context()).thenReturn(spanContext);
        when(spanContext.traceId()).thenReturn(traceId);
        when(tracer.currentSpan()).thenReturn(span);
    }

    @Test
    @DisplayName("有当前 span 时回写标准 traceId 到响应头")
    void writesTraceIdFromSpan() throws Exception {
        stubCurrentSpan("4bf92f3577b34da6a3ce929d0e0e4736");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/cases");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(response).setHeader(eq(TraceIdFilter.HEADER_TRACE_ID),
                eq("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    @DisplayName("无当前 span 时兜底生成 UUID 并回写响应头")
    void fallsBackToGeneratedTraceIdWithoutSpan() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/cases");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        // 兜底生成：仍回写响应头，保证链路不中断
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(TraceIdFilter.HEADER_TRACE_ID), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(32, captor.getValue().length(),
                "兜底 traceId 应为 32 位 UUID");
    }

    @Test
    @DisplayName("请求结束后清理 MDC")
    void clearsMdcAfterRequest() throws Exception {
        stubCurrentSpan("cleanup-test-trace");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/cases");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(MDC.get(TraceIdFilter.MDC_KEY), "MDC 应已清理");
    }

    @Test
    @DisplayName("业务抛异常时仍能清理 MDC（finally 保证）")
    void clearsMdcEvenOnException() throws Exception {
        stubCurrentSpan("exception-test-trace");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/cases");
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            throw new RuntimeException("boom");
        }).when(chain).doFilter(any(), any());

        try {
            filter.doFilter(request, response, chain);
        } catch (RuntimeException expected) {
            // 预期异常
        }

        assertNull(MDC.get(TraceIdFilter.MDC_KEY), "异常后 MDC 也应被清理");
    }
}
