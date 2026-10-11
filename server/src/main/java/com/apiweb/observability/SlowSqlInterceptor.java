package com.apiweb.observability;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 慢 SQL 监控拦截器：统计每条 SQL 的执行耗时，超过阈值打 WARN 日志。
 *
 * <p>通过 MyBatis 插件机制拦截 {@link StatementHandler#query(Statement, ResultHandler)} 与
 * {@link StatementHandler#update(Statement)}，包裹原调用并记录耗时。超过阈值的 SQL 以 WARN 级别
 * 输出（含耗时、SQL 文本），便于运维/开发从日志快速定位慢查询。所有 SQL 均以 DEBUG 级别
 * 输出，便于按需开启全量 SQL 追踪。
 *
 * <h3>注册方式</h3>
 * 在 {@code MybatisPlusConfig} 中作为 {@code InnerInterceptor} 或独立 {@code Interceptor} Bean
 * 注入到 {@code MybatisPlusInterceptor} 的插件链中，注意其 {@code order} 需在分页插件之后，
 * 以保证拿到的 SQL 已包含分页改写。
 *
 * <h3>阈值配置</h3>
 * 阈值来自配置 {@code apiweb.observability.slow-sql-threshold-ms}（默认 1000ms），
 * 通过 Spring {@code @Value} 注入，可被环境变量 {@code OBS_SLOW_SQL_THRESHOLD_MS} 覆盖。
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query",
                args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlInterceptor.class);

    /** 慢 SQL 阈值（毫秒），由 Spring 注入，默认 1000ms */
    private final long thresholdMs;

    /**
     * 构造拦截器。
     *
     * @param thresholdMs 慢 SQL 阈值（毫秒），超过该值打 WARN
     */
    public SlowSqlInterceptor(long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    /**
     * 包裹 SQL 执行，统计耗时并按需输出日志。
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            String sql = extractSql(invocation);
            if (cost >= thresholdMs) {
                log.warn("慢SQL检测 cost={}ms threshold={}ms sql={}", cost, thresholdMs, sql);
            } else {
                log.debug("SQL执行 cost={}ms sql={}", cost, sql);
            }
        }
    }

    /**
     * 从 Invocation 中提取 SQL 文本（StatementHandler → BoundSql）。
     * 反射失败时返回占位文本，不抛出异常，保证监控本身不影响业务。
     *
     * @param invocation 拦截到的调用
     * @return SQL 文本，提取失败返回 "unknown"
     */
    private String extractSql(Invocation invocation) {
        try {
            StatementHandler handler = (StatementHandler) invocation.getTarget();
            BoundSql boundSql = handler.getBoundSql();
            return boundSql.getSql().replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
