package com.apiweb.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TraceIdFilter} 链路追踪过滤器测试。
 *
 * <p>核心契约：
 * <ol>
 *   <li>透传上游 {@code X-Trace-Id}，不重新生成（保证跨服务链路连续）</li>
 *   <li>无上游 traceId 时生成非空新 traceId</li>
 *   <li>无论何种来源，统一回写响应头 {@code X-Trace-Id}</li>
 *   <li>请求结束（成功或异常）后 MDC 与 TraceContext 都被清理，防止线程复用串号</li>
 * </ol>
 */
class TraceIdFilterTest {

    private TraceIdFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        TraceContext.clear();
    }

    @Test
    @DisplayName("透传上游 traceId 且回写响应头")
    void propagatesUpstreamTraceId() throws Exception {
        when(request.getHeader(TraceIdFilter.HEADER_TRACE_ID)).thenReturn("upstream-123");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/cases");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(response).setHeader(TraceIdFilter.HEADER_TRACE_ID, "upstream-123");
    }

    @Test
    @DisplayName("无上游 traceId 时生成非空新 traceId")
    void generatesTraceIdWhenAbsent() throws Exception {
        when(request.getHeader(TraceIdFilter.HEADER_TRACE_ID)).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/cases");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // 捕获回写的 traceId 进行断言
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(TraceIdFilter.HEADER_TRACE_ID), captor.capture());
        String traceId = captor.getValue();
        assertNotNull(traceId, "应生成非空 traceId");
        assertFalse(traceId.isBlank());
        // UUID 去连字符后为 32 位
        assertEquals(32, traceId.length(), "traceId 应为 32 位（UUID 去连字符）");
    }

    @Test
    @DisplayName("请求结束后清理 MDC 与 TraceContext")
    void clearsContextAfterRequest() throws Exception {
        when(request.getHeader(TraceIdFilter.HEADER_TRACE_ID)).thenReturn("cleanup-test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/cases");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(MDC.get(TraceIdFilter.MDC_KEY), "MDC 应已清理");
        assertNull(TraceContext.get(), "TraceContext 应已清理");
    }

    @Test
    @DisplayName("业务抛异常时仍能清理上下文（finally 保证）")
    void clearsContextEvenOnException() throws Exception {
        when(request.getHeader(TraceIdFilter.HEADER_TRACE_ID)).thenReturn("exception-test");
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
        assertNull(TraceContext.get(), "异常后 TraceContext 也应被清理");
    }
}
