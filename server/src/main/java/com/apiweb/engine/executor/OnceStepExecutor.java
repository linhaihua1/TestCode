package com.apiweb.engine.executor;

import com.apiweb.engine.CaseRunner;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.StepExecutor;
import com.apiweb.engine.step.StepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 仅一次控制器（{@link StepType#ONCE}）。
 *
 * <p>同一任务执行期间只跑一次（按 stepId 标记）。后续遇到直接跳过。
 * 适用于"任务内登录一次"等场景。
 */
@Component
@RequiredArgsConstructor
public class OnceStepExecutor implements StepExecutor {

    private final CaseRunner caseRunner;

    @Override
    public StepType type() { return StepType.ONCE; }

    @Override
    public EngineDtos.StepResult execute(StepDef step, StepContext ctx) {
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status("skipped")
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .build();
        if (step.getId() != null && ctx.isOnceExecuted(step.getId())) {
            sr.setError("仅一次控制器已执行过,跳过");
            return sr;
        }
        long start = System.currentTimeMillis();
        try {
            caseRunner.runSteps(step.getChildren(), ctx);
            sr.setStatus("success");
            ctx.markOnceExecuted(step.getId());
        } catch (Exception e) {
            sr.setStatus("error");
            sr.setError("ONCE 控制器异常: " + e.getMessage());
        }
        sr.setDurationMs(System.currentTimeMillis() - start);
        return sr;
    }
}