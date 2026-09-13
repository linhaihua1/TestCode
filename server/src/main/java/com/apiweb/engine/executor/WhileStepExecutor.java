package com.apiweb.engine.executor;

import com.apiweb.config.EngineProperties;
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
import com.apiweb.engine.RunStatus;

/**
 * WHILE 循环控制器（{@link StepType#WHILE}）。
 *
 * <p>条件为 true 时重复执行 children,有最大迭代次数（防死循环）+ 超时双重保护。
 * 兜底上限来自 {@link EngineProperties}（{@code apiweb.engine.while-max-*}）。
 */
@Component
@RequiredArgsConstructor
public class WhileStepExecutor implements StepExecutor {

    /** 用 ObjectProvider 打断与 CaseRunner 的循环依赖，见 ForStepExecutor 注释。 */
    private final ObjectProvider<CaseRunner> caseRunnerProvider;
    private final EngineProperties engine;

    private CaseRunner runner() {
        CaseRunner r = caseRunnerProvider.getIfAvailable();
        if (r == null) throw new IllegalStateException("CaseRunner 尚未就绪");
        return r;
    }

    @Override
    public StepType type() { return StepType.WHILE; }

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
            Integer maxIters = step.cfgInt("maxIterations");
            if (maxIters == null) maxIters = engine.getWhileMaxIterations();
            Long timeoutMs = step.cfgLong("timeoutMs");
            if (timeoutMs == null) timeoutMs = engine.getWhileMaxTimeoutMs();
            long deadline = System.currentTimeMillis() + timeoutMs;
            int iterations = 0;
            List<StepDef> children = step.getChildren() != null ? step.getChildren() : List.of();
            CaseRunner caseRunner = runner();
            while (caseRunner.evalCondition(condition == null ? "" : condition, ctx.getResolver())) {
                if (++iterations > maxIters || System.currentTimeMillis() > deadline) {
                    sr.setStatus(RunStatus.FAILED.getCode());
                    sr.setError("WHILE 循环超限(超过 " + maxIters + " 次或 " + timeoutMs + "ms)");
                    break;
                }
                caseRunner.runSteps(children, ctx);
            }
        } catch (Exception e) {
            sr.setStatus(RunStatus.ERROR.getCode());
            sr.setError("WHILE 控制器异常: " + e.getMessage());
        }
        sr.setDurationMs(System.currentTimeMillis() - start);
        return sr;
    }
}