package com.apiweb.observability;

import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SlowSqlInterceptor} 慢 SQL 监控拦截器测试。
 *
 * <p>重点验证：
 * <ol>
 *   <li>正常执行时透传原调用结果，不改变业务语义</li>
 *   <li>SQL 提取失败（反射异常）时不抛出异常，监控不阻断业务</li>
 *   <li>业务抛异常时原样抛出（finally 只做耗时统计，不吞异常）</li>
 * </ol>
 */
class SlowSqlInterceptorTest {

    @Test
    @DisplayName("正常执行透传结果")
    void returnsProceedResult() throws Throwable {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor(1000L);
        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenReturn("ok");
        when(invocation.getTarget()).thenReturn(null);

        Object result = interceptor.intercept(invocation);

        assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertEquals("ok", result);
    }

    @Test
    @DisplayName("SQL 提取失败时不抛出异常（监控不阻断业务）")
    void extractSqlFailureDoesNotThrow() throws Throwable {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor(1000L);
        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenReturn("ok");
        // target 返回 null，模拟反射提取 SQL 失败
        when(invocation.getTarget()).thenReturn(null);

        Object result = interceptor.intercept(invocation);

        org.junit.jupiter.api.Assertions.assertEquals("ok", result, "SQL 提取失败不应影响业务结果");
    }

    @Test
    @DisplayName("业务抛异常时原样抛出，不吞异常")
    void rethrowsBusinessException() throws Throwable {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor(1000L);
        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenThrow(new RuntimeException("sql fail"));
        when(invocation.getTarget()).thenReturn(null);

        assertThrows(RuntimeException.class, () -> interceptor.intercept(invocation),
                "业务异常应原样抛出");
    }
}
