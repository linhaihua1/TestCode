package com.apiweb.engine;

import lombok.Getter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 步骤执行时的共享上下文。
 *
 * <p>线程安全：每个用例执行创建一个新的 {@link StepContext},通过 {@code ThreadLocal} 化的
 * ONCE 控制器状态保证同一任务内多个用例只执行一次。
 */
@Getter
public class StepContext {

    /** 用例/任务共享变量池（按优先级合并后） */
    private final VariablesResolver resolver;
    /** 本次执行结果累积 */
    private final EngineDtos.ExecutionResult result;
    /** 任务级状态：ONCE 控制器已执行过的 step id */
    private final Set<String> onceExecuted = ConcurrentHashMap.newKeySet();
    /** 公共用例执行深度（防无限递归） */
    private int refCaseDepth = 0;
    /** 用例起始时间戳 */
    private final long startMillis = System.currentTimeMillis();

    /**
     * 创建一条用例执行所需的上下文。
     *
     * @param resolver 已按优先级合并好的变量解析器（来自 VariableMerger）
     * @param result   本次执行结果累积对象，各步骤把自己的 StepResult 追加进去
     */
    public StepContext(VariablesResolver resolver, EngineDtos.ExecutionResult result) {
        this.resolver = resolver;
        this.result = result;
    }

    /**
     * 标记某个 ONCE 控制器已执行过（按 stepId 去重）。
     *
     * <p>同一个任务内遇到相同 stepId 的 ONCE 步骤时，通过 {@link #isOnceExecuted} 判断是否需要跳过。
     *
     * @param stepId 步骤 id（为 null 时忽略）
     */
    public void markOnceExecuted(String stepId) {
        if (stepId != null) onceExecuted.add(stepId);
    }

    /**
     * 判断该步骤的 ONCE 控制器是否已经执行过。
     *
     * @param stepId 步骤 id
     * @return true 表示已执行过（应跳过）
     */
    public boolean isOnceExecuted(String stepId) {
        return stepId != null && onceExecuted.contains(stepId);
    }

    /**
     * 提取变量写入 resolver 和 result.variables。
     *
     * <p>两步同时写，保证「变量池」与「报告快照」一致，方便报告展示用例用到了哪些变量。
     * value 为 null 时统一存为空字符串，避免后续 resolve 出现 NPE。
     *
     * @param name  变量名
     * @param value 变量值
     */
    public void putVariable(String name, String value) {
        resolver.put(name, value == null ? "" : value);
        result.getVariables().put(name, value == null ? "" : value);
    }

    /**
     * 判断引用公共用例的嵌套深度是否已超限。
     *
     * <p>用 {@code > max} 而非 {@code >= max}：当 depth 正好等于 max 时仍允许再进一层，
     * 第 max+1 层才被拦截，从而把最大嵌套层数控制在 max 层。
     *
     * @param max 允许的最大深度（RefPublicCaseExecutor 中定义为 5）
     * @return true 表示已超限，应停止继续嵌套
     */
    public boolean isMaxDepthReached(int max) {
        return refCaseDepth > max;
    }

    /**
     * 设置当前引用公共用例的嵌套深度。
     *
     * <p>进入公共用例前 {@code +1}，执行完后再 {@code -1}（见 RefPublicCaseExecutor），
     * 用于配合 {@link #isMaxDepthReached} 防止无限递归。
     *
     * @param depth 新的嵌套深度
     */
    public void setRefCaseDepth(int depth) {
        this.refCaseDepth = depth;
    }
}