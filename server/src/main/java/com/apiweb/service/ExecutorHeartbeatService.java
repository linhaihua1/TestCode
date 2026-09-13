package com.apiweb.service;

import com.apiweb.config.ExecutorProperties;
import com.apiweb.engine.ExecutorStatus;
import com.apiweb.engine.TaskType;
import com.apiweb.entity.ExecutorNodeEntity;
import com.apiweb.mapper.ExecutorNodeMapper;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 执行机心跳注册与资源池管理。
 * 设计：
 *   * 执行机节点启动后调用 register()，写 t_executor_node + Redis key（TTL = heartbeat-timeout）
 *   * 每个执行机周期性上报心跳（HTTP 或内部 @Scheduled）
 *   * 平台侧定时扫描：心跳超过 timeout 的节点标记为 offline，从资源池剔除
 *
 * <p>心跳超时、扫描间隔、Redis key 前缀、默认能力均来自 {@link ExecutorProperties}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorHeartbeatService {

    /** 无法解析本机主机名时的占位值 */
    private static final String UNKNOWN_HOST = "unknown";

    private final ExecutorNodeMapper executorNodeMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutorProperties executorProperties;

    /**
     * 本执行机节点 ID（hostname:pid）。
     */
    public static String localNodeId() {
        String hostname;
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = UNKNOWN_HOST;
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        return hostname + ":" + pid;
    }

    /**
     * 执行机注册/心跳上报。
     */
    public ExecutorNodeEntity heartbeat(String nodeId, String name, List<String> capabilities) {
        ExecutorNodeEntity node = executorNodeMapper.selectById(nodeId);
        Instant now = Instant.now();
        if (node == null) {
            node = new ExecutorNodeEntity();
            node.setId(nodeId);
            node.setName(name == null ? nodeId : name);
            node.setCapabilities(JsonUtils.toJson(
                    capabilities == null ? executorProperties.getDefaultCapabilities() : capabilities));
            node.setStatus(ExecutorStatus.ONLINE.getCode());
            node.setLastHeartbeat(now);
            node.setCreatedAt(now);
            node.setUpdatedAt(now);
            executorNodeMapper.insert(node);
        } else {
            node.setStatus(ExecutorStatus.ONLINE.getCode());
            node.setLastHeartbeat(now);
            node.setUpdatedAt(now);
            executorNodeMapper.updateById(node);
        }
        // Redis TTL 心跳（供快速判活）
        redisTemplate.opsForValue().set(
                executorProperties.getRedisKeyPrefix() + nodeId, now.toEpochMilli(),
                executorProperties.getHeartbeatTimeout(), TimeUnit.SECONDS);
        return node;
    }

    /**
     * 平台侧定时任务：剔除心跳超时的执行机。
     *
     * <p>扫描间隔来自配置 {@code apiweb.executor.cleanup-interval-ms}。
     */
    @Scheduled(fixedDelayString = "${apiweb.executor.cleanup-interval-ms:30000}",
            initialDelayString = "${apiweb.executor.cleanup-interval-ms:30000}")
    public void evictStaleNodes() {
        Instant deadline = Instant.now().minusSeconds(executorProperties.getHeartbeatTimeout());
        List<ExecutorNodeEntity> stale = executorNodeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExecutorNodeEntity>()
                        .eq(ExecutorNodeEntity::getStatus, ExecutorStatus.ONLINE.getCode())
                        .lt(ExecutorNodeEntity::getLastHeartbeat, deadline));
        for (ExecutorNodeEntity node : stale) {
            node.setStatus(ExecutorStatus.OFFLINE.getCode());
            node.setUpdatedAt(Instant.now());
            executorNodeMapper.updateById(node);
            log.info("执行机心跳超时，已下线: {} (最后心跳 {})", node.getId(), node.getLastHeartbeat());
        }
    }

    /**
     * 查询资源池（在线节点列表）。
     */
    public List<ExecutorNodeEntity> resourcePool() {
        return executorNodeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExecutorNodeEntity>()
                        .in(ExecutorNodeEntity::getStatus,
                                ExecutorStatus.ONLINE.getCode(), ExecutorStatus.BUSY.getCode())
                        .orderByDesc(ExecutorNodeEntity::getLastHeartbeat));
    }

    /**
     * 按能力挑选一台可用执行机（简单轮询：取最新心跳的空闲节点）。
     */
    public ExecutorNodeEntity pickExecutor(String capability) {
        List<ExecutorNodeEntity> pool = resourcePool();
        for (ExecutorNodeEntity node : pool) {
            List<String> caps = JsonUtils.fromJson(
                    node.getCapabilities() == null ? "[]" : node.getCapabilities(), List.class);
            if (caps.contains(capability)
                    && ExecutorStatus.ONLINE.getCode().equals(node.getStatus())) {
                return node;
            }
        }
        return null;
    }

    /**
     * 绑定/解绑节点当前任务。
     */
    public void bindTask(String nodeId, String runId) {
        ExecutorNodeEntity node = executorNodeMapper.selectById(nodeId);
        if (node != null) {
            node.setStatus(runId == null
                    ? ExecutorStatus.ONLINE.getCode() : ExecutorStatus.BUSY.getCode());
            node.setCurrentTask(runId);
            node.setUpdatedAt(Instant.now());
            executorNodeMapper.updateById(node);
        }
    }
}
