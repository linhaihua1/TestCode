package com.apiweb.engine.executor;

import com.apiweb.engine.CaseRunner;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.StepExecutor;
import com.apiweb.engine.step.StepType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.apiweb.engine.RunStatus;

/**
 * IF-ELSE 条件分支控制器（{@link StepType#IF}）。
 *
 * <p>config.condition 为 JS 表达式,true 执行 children,false 执行 elseBranch（可选）。
 */
@Component
@RequiredArgsConstructor
public class IfStepExecutor implements StepExecutor {

    /** 用 ObjectProvider 打断与 CaseRunner 的循环依赖，见 ForStepExecutor 注释。 */
    private final ObjectProvider<CaseRunner> caseRunnerProvider;

    /**
     * 延迟获取 CaseRunner（通过 ObjectProvider 避免构造期循环依赖）。
     *
     * @return 可用的 CaseRunner 实例
     * @throws IllegalStateException 容器尚未就绪（理论上不会发生）
     */
    private CaseRunner runner() {
        CaseRunner r = caseRunnerProvider.getIfAvailable();
        if (r == null) throw new IllegalStateException("CaseRunner 尚未就绪");
        return r;
    }

    @Override
    public StepType type() { return StepType.IF; }

    @Override
    public EngineDtos.StepResult execute(StepDef step, StepContext ctx) {
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status(RunStatus.SUCCESS.getCode())
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .build();
        long start = System.currentTimeMillis();
        try {
            String condition = step.cfg("condition");
            CaseRunner caseRunner = runner();
            boolean matched = caseRunner.evalCondition(condition == null ? "" : condition, ctx.getResolver());
            List<StepDef> target = matched
                    ? (step.getChildren() != null ? step.getChildren() : List.of())
                    : readElseBranch(step.cfgRaw("elseBranch"));
            caseRunner.runSteps(target, ctx);
        } catch (Exception e) {
            sr.setStatus(RunStatus.ERROR.getCode());
            sr.setError("IF 控制器异常: " + e.getMessage());
        }
        sr.setDurationMs(System.currentTimeMillis() - start);
        return sr;
    }

    /**
     * 读取 IF 的 else 分支步骤。
     *
     * <p>else 分支以步骤列表形式内联在 config 的 elseBranch 字段里（与 children 平级），
     * 用 {@link StepDef#fromMap} 转为标准步骤定义后执行。
     *
     * @param raw 步骤 config 中的 elseBranch 字段（List 或 JSON 数组字符串）
     * @return else 分支步骤列表（缺失或非数组时返回空列表）
     */
    @SuppressWarnings("unchecked")
    private List<StepDef> readElseBranch(Object raw) {
        List<StepDef> out = new ArrayList<>();
        for (Object item : asList(raw)) {
            if (item instanceof Map<?, ?> m) {
                out.add(StepDef.fromMap((Map<String, Object>) m));
            }
        }
        return out;
    }
}