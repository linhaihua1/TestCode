package com.apiweb.config;

import com.apiweb.engine.TaskType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 执行机资源池配置。
 *
 * <p>对应 {@code application.yml} 的 {@code apiweb.executor.*}：
 * <pre>
 * apiweb:
 *   executor:
 *     heartbeat-timeout: 60                                   # 秒
 *     cleanup-interval-ms: 30000                              # 心跳超时扫描间隔
 *     redis-key-prefix: apiweb:executor:heartbeat:
 *     default-capabilities: [api, ui, perf]                   # 注册时未声明能力时的默认值
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "apiweb.executor")
public class ExecutorProperties {

    /** 心跳超时（秒），超时视为离线并从资源池剔除 */
    private long heartbeatTimeout = 60;

    /** 平台侧扫描超时节点的间隔（毫秒） */
    private long cleanupIntervalMs = 30_000;

    /** 执行机心跳在 Redis 中的 key 前缀 */
    private String redisKeyPrefix = "apiweb:executor:heartbeat:";

    /** 注册时未声明能力时使用的默认能力列表 */
    private List<String> defaultCapabilities = List.of(TaskType.API.getCode());
}
