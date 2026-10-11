package com.apiweb.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TraceContext} 链路追踪上下文测试。
 *
 * <p>重点验证 ThreadLocal 的写入/读取/清理语义：清理后必须返回 null，
 * 否则 Tomcat 线程复用会导致后续请求串号（traceId 污染）。
 */
class TraceContextTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("设置后能正确读取 traceId")
    void setThenGet() {
        TraceContext.set("abc123");
        assertEquals("abc123", TraceContext.get());
    }

    @Test
    @DisplayName("未设置时返回 null")
    void getWithoutSetReturnsNull() {
        assertNull(TraceContext.get());
    }

    @Test
    @DisplayName("清理后返回 null，防止线程复用串号")
    void clearRemovesTraceId() {
        TraceContext.set("abc123");
        TraceContext.clear();
        assertNull(TraceContext.get());
    }
}
