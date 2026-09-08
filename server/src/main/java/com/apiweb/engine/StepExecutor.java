package com.apiweb.engine;

import com.apiweb.engine.step.StepType;

/**
 * 步骤执行器接口（按 {@link StepType} 路由）。
 *
 * <p>每种步骤类型由一个 {@link StepExecutor} 实现,通过 {@link StepExecutorRegistry} 分发。
 * 实现类用 {@link org.springframework.stereotype.Component} 注册到 Spring 容器,
 * 在 {@link StepExecutorRegistry} 启动时收集。
 *
 * <h3>执行上下文</h3>
 * {@link StepContext} 包含：
 * <ul>
 *   <li>当前步骤定义</li>
 *   <li>共享变量池</li>
 *   <li>执行结果累积</li>
 *   <li>任务级状态（如 ONCE 控制器已执行标记）</li>
 * </ul>
 */
public interface StepExecutor {

    /**
     * 支持的步骤类型。
     */
    StepType type();

    /**
     * 执行单步。
     *
     * @param step    当前步骤定义
     * @param context 执行上下文
     * @return 步骤结果（status: success/failed/error/skipped）
     */
    EngineDtos.StepResult execute(StepDef step, StepContext context) throws Exception;

    /**
     * 工具：从 config 中读取 KV 列表 [{key,value,enabled}]。
     */
    @SuppressWarnings("unchecked")
    default java.util.List<EngineDtos.KV> readKvList(Object raw) {
        java.util.List<EngineDtos.KV> out = new java.util.ArrayList<>();
        if (raw instanceof java.util.List<?> list) {
            for (Object item : list) {
                if (item instanceof java.util.Map<?, ?> m) {
                    Boolean enabled = m.get("enabled") instanceof Boolean b ? b : true;
                    out.add(new EngineDtos.KV(
                            toStr(m.get("key")),
                            toStr(m.get("value")),
                            enabled));
                }
            }
        }
        return out;
    }

    static String toStr(Object o) { return o == null ? null : String.valueOf(o); }
    static String toStr(Object o, String def) { return o == null ? def : String.valueOf(o); }
}