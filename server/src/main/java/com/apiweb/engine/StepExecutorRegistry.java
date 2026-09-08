package com.apiweb.engine;

import com.apiweb.engine.step.StepType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 步骤执行器注册表（按 {@link StepType} 路由到对应 {@link StepExecutor}）。
 *
 * <p>Spring 启动时自动收集所有 {@link StepExecutor} Bean,按 {@code type()} 索引。
 * 找不到对应实现时回退到 HTTP_REQUEST（兼容旧数据）。
 */
@Slf4j
@Component
public class StepExecutorRegistry {

    private final Map<StepType, StepExecutor> executors = new EnumMap<>(StepType.class);

    @Autowired
    public void register(List<StepExecutor> all) {
        for (StepExecutor e : all) {
            executors.put(e.type(), e);
            log.info("注册步骤执行器: {} -> {}", e.type(), e.getClass().getSimpleName());
        }
    }

    /**
     * 获取对应步骤类型的执行器。
     */
    public StepExecutor get(StepType type) {
        return executors.getOrDefault(type, executors.get(StepType.HTTP_REQUEST));
    }
}