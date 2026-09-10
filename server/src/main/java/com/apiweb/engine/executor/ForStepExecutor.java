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

/**
 * FOR 循环控制器（{@link StepType#FOR}）。
 *
 * <ul>
 *   <li>mode=count：循环 {@code count} 次,每次注入变量 {@code loopVar}（默认 i）</li>
 *   <li>mode=list：遍历 {@code listVar} 引用的列表,每次注入 {@code loopVar}=当前元素</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ForStepExecutor implements StepExecutor {

    /**
     * 用 ObjectProvider 延迟获取 CaseRunner。
     *
     * <p>CaseRunner 依赖 StepExecutorRegistry，而 registry 又收集本执行器，
     * 直接注入 CaseRunner 会形成循环依赖（BeanCurrentlyInCreationException）。
     * ObjectProvider 只在真正执行时取 Bean，打断构造期环路。
     */
    private final ObjectProvider<CaseRunner> caseRunnerProvider;

    private CaseRunner runner() {
        CaseRunner r = caseRunnerProvider.getIfAvailable();
        if (r == null) throw new IllegalStateException("CaseRunner 尚未就绪");
        return r;
    }

    @Override
    public StepType type() { return StepType.FOR; }

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
            String mode = step.cfg("mode");
            String loopVar = step.cfg("loopVar");
            if (loopVar == null || loopVar.isBlank()) loopVar = "i";
            List<StepDef> children = step.getChildren() != null ? step.getChildren() : List.of();
            if (children.isEmpty()) {
                sr.setDurationMs(System.currentTimeMillis() - start);
                return sr;
            }
            CaseRunner caseRunner = runner();

            if ("list".equalsIgnoreCase(mode)) {
                String listVar = step.cfg("listVar");
                String listJson = listVar == null ? null : ctx.getResolver().get(listVar);
                List<String> items = parseList(listJson);
                int index = 0;
                for (String item : items) {
                    ctx.putVariable(loopVar, item);
                    ctx.putVariable(loopVar + "__index", String.valueOf(index++));
                    caseRunner.runSteps(children, ctx);
                }
            } else {
                Integer count = step.cfgInt("count");
                if (count == null) count = 1;
                Integer stepInc = step.cfgInt("step");
                if (stepInc == null || stepInc == 0) stepInc = 1;
                for (int i = 0; i < count; i++) {
                    ctx.putVariable(loopVar, String.valueOf(i));
                    ctx.putVariable("__index__", String.valueOf(i));
                    caseRunner.runSteps(children, ctx);
                }
            }
        } catch (Exception e) {
            sr.setStatus("error");
            sr.setError("FOR 控制器异常: " + e.getMessage());
        }
        sr.setDurationMs(System.currentTimeMillis() - start);
        return sr;
    }

    /**
     * 解析列表字符串（逗号分隔或 JSON 数组）。
     */
    private List<String> parseList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<?> list = com.apiweb.util.JsonUtils.fromJson(trimmed, List.class);
                if (list != null) for (Object o : list) out.add(o == null ? "" : String.valueOf(o));
                return out;
            } catch (Exception ignored) {
            }
        }
        for (String s : trimmed.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}