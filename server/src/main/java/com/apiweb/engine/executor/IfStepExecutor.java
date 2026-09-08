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
import java.util.List;
import java.util.Map;

/**
 * IF-ELSE 条件分支控制器（{@link StepType#IF}）。
 *
 * <p>config.condition 为 JS 表达式,true 执行 children,false 执行 elseBranch（可选）。
 */
@Component
@RequiredArgsConstructor
public class IfStepExecutor implements StepExecutor {

    private final CaseRunner caseRunner;

    @Override
    public StepType type() { return StepType.IF; }

    @Override
    public EngineDtos.StepResult execute(StepDef step, StepContext ctx) {
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status("success")
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .build();
        long start = System.currentTimeMillis();
        try {
            String condition = step.cfg("condition");
            boolean matched = caseRunner.evalCondition(condition == null ? "" : condition, ctx.getResolver());
            List<StepDef> target = matched
                    ? (step.getChildren() != null ? step.getChildren() : List.of())
                    : readElseBranch(step.cfg("elseBranch"));
            caseRunner.runSteps(target, ctx);
        } catch (Exception e) {
            sr.setStatus("error");
            sr.setError("IF 控制器异常: " + e.getMessage());
        }
        sr.setDurationMs(System.currentTimeMillis() - start);
        return sr;
    }

    @SuppressWarnings("unchecked")
    private List<StepDef> readElseBranch(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<StepDef> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add(StepDef.fromMap((Map<String, Object>) m));
            }
        }
        return out;
    }
}