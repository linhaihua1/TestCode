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
     * 工具：把 config 中的字段安全解析为 List。
     *
     * <p>兼容三种形态，避免「结构化字段被字符串化后恒为空」的静默失效：</p>
     * <ol>
     *   <li>{@code List} —— 前端扁平步骤 JSON 经 {@link StepDef#fromMap} 解析后的正常形态；</li>
     *   <li>{@code String} —— 历史数据 / 手工配置里把数组存成 JSON 文本的情况；</li>
     *   <li>其他（null / Map / 数字）—— 返回空列表，不抛异常。</li>
     * </ol>
     *
     * @param raw config 字段原始值
     * @return 解析后的列表（不会为 null）
     */
    default java.util.List<?> asList(Object raw) {
        if (raw instanceof java.util.List<?> list) {
            return list;
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                java.util.List<?> parsed = com.apiweb.util.JsonUtils.fromJson(s, java.util.List.class);
                return parsed == null ? java.util.List.of() : parsed;
            } catch (Exception ignored) {
                return java.util.List.of();
            }
        }
        return java.util.List.of();
    }

    /**
     * 工具：从 config 中读取 KV 列表 [{key,value,enabled}]。
     *
     * <p>字段缺失时自动补默认值：enabled 缺省为 true，value 为 null 时存 null。
     *
     * @param raw 步骤 config 中的对应字段（List&lt;Map&gt; 或 JSON 数组字符串）
     * @return 解析后的 KV 列表
     */
    default java.util.List<EngineDtos.KV> readKvList(Object raw) {
        java.util.List<EngineDtos.KV> out = new java.util.ArrayList<>();
        for (Object item : asList(raw)) {
            if (item instanceof java.util.Map<?, ?> m) {
                Boolean enabled = m.get("enabled") instanceof Boolean b ? b : true;
                out.add(new EngineDtos.KV(
                        toStr(m.get("key")),
                        toStr(m.get("value")),
                        enabled));
            }
        }
        return out;
    }

    /** 工具：对象转字符串，null 返回 null。 */
    static String toStr(Object o) { return o == null ? null : String.valueOf(o); }
    /** 工具：对象转字符串，null 返回默认值 def。 */
    static String toStr(Object o, String def) { return o == null ? def : String.valueOf(o); }
}