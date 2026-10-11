package com.apiweb.config;

import com.apiweb.observability.SlowSqlInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * MyBatis-Plus 配置：分页插件 + 时间字段（UTC Instant）自动填充 + 慢 SQL 监控。
 */
@Configuration
public class MybatisPlusConfig {

    /** 慢 SQL 阈值（毫秒），来自配置 apiweb.observability.slow-sql-threshold-ms */
    @Value("${apiweb.observability.slow-sql-threshold-ms:1000}")
    private long slowSqlThresholdMs;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 慢 SQL 监控拦截器：独立 Bean 注入 MyBatis 插件链。
     *
     * <p>注意：此拦截器必须注册为独立的 MyBatis {@link org.apache.ibatis.plugin.Interceptor}，
     * 不能作为 MybatisPlus 的 InnerInterceptor（后者只作用于 executor 层，而本拦截器作用于
     * statement 层以拿到最终 SQL 与精确耗时）。Spring Boot 会自动把它装配进 SqlSessionFactory。
     */
    @Bean
    public SlowSqlInterceptor slowSqlInterceptor() {
        return new SlowSqlInterceptor(slowSqlThresholdMs);
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                Instant now = Instant.now();
                strictInsertFill(metaObject, "createdAt", Instant.class, now);
                strictInsertFill(metaObject, "updatedAt", Instant.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
            }
        };
    }
}
