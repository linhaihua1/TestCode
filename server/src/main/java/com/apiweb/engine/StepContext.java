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

    public StepContext(VariablesResolver resolver, EngineDtos.ExecutionResult result) {
        this.resolver = resolver;
        this.result = result;
    }

    public void markOnceExecuted(String stepId) {
        if (stepId != null) onceExecuted.add(stepId);
    }

    public boolean isOnceExecuted(String stepId) {
        return stepId != null && onceExecuted.contains(stepId);
    }

    /** 提取变量写入 resolver 和 result.variables */
    public void putVariable(String name, String value) {
        resolver.put(name, value == null ? "" : value);
        result.getVariables().put(name, value == null ? "" : value);
    }

    public boolean isMaxDepthReached(int max) {
        return refCaseDepth > max;
    }

    public void setRefCaseDepth(int depth) {
        this.refCaseDepth = depth;
    }
}