package com.apiweb.engine.executor;

import com.apiweb.engine.CaseRunner;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.StepExecutor;
import com.apiweb.engine.step.StepType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 引用公共用例（{@link StepType#REF_PUBLIC_CASE}）。
 *
 * <p>把另一个用例的步骤作为当前步骤执行,支持参数覆盖（{@code overrides}）。
 * 嵌套深度最多 5 层,防止无限递归。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefPublicCaseExecutor implements StepExecutor {

    private static final int MAX_DEPTH = 5;
    private final CaseRunner caseRunner;

    @Override
    public StepType type() { return StepType.REF_PUBLIC_CASE; }

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
            if (ctx.isMaxDepthReached(MAX_DEPTH)) {
                sr.setStatus("failed");
                sr.setError("引用公共用例嵌套超过 " + MAX_DEPTH + " 层,已截断");
                return sr;
            }
            // 取出被引用的步骤,这里由 caseRunner 提供
            String publicCaseId = step.cfg("publicCaseId");
            if (publicCaseId == null || publicCaseId.isBlank()) {
                sr.setStatus("failed");
                sr.setError("publicCaseId 不能为空");
                return sr;
            }
            // overrides 优先于被引用用例的变量
            Object overridesRaw = step.cfg("overrides");
            if (overridesRaw instanceof List<?> ol) {
                for (Object item : ol) {
                    if (item instanceof Map<?, ?> o) {
                        Object name = o.get("name");
                        Object value = o.get("value");
                        if (name != null) ctx.putVariable(String.valueOf(name),
                                value == null ? "" : String.valueOf(value));
                    }
                }
            }
            List<StepDef> referenced = caseRunner.loadCaseSteps(publicCaseId);
            ctx.setRefCaseDepth(ctx.getRefCaseDepth() + 1);
            caseRunner.runSteps(referenced, ctx);
            ctx.setRefCaseDepth(ctx.getRefCaseDepth() - 1);
        } catch (Exception e) {
            log.warn("引用公共用例失败: {}", e.getMessage());
            sr.setStatus("error");
            sr.setError("引用公共用例失败: " + e.getMessage());
        }
        sr.setDurationMs(System.currentTimeMillis() - start);
        return sr;
    }
}