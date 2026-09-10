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
 * 事务控制器（{@link StepType#TRANSACTION}）。
 *
 * <p>把子步骤视为一个事务,根据 {@code successRule} 判定整体结果：
 * <ul>
 *   <li>all_pass：所有子步骤成功才视为事务成功</li>
 *   <li>one_pass：任一成功即视为事务成功</li>
 *   <li>ignore：忽略子步骤结果,事务本身永远成功</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TransactionStepExecutor implements StepExecutor {

    /** 用 ObjectProvider 打断与 CaseRunner 的循环依赖，见 ForStepExecutor 注释。 */
    private final ObjectProvider<CaseRunner> caseRunnerProvider;

    private CaseRunner runner() {
        CaseRunner r = caseRunnerProvider.getIfAvailable();
        if (r == null) throw new IllegalStateException("CaseRunner 尚未就绪");
        return r;
    }

    @Override
    public StepType type() { return StepType.TRANSACTION; }

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
            List<StepDef> children = step.getChildren() != null ? step.getChildren() : List.of();
            String rule = step.cfg("successRule");
            if (rule == null || rule.isBlank()) rule = "all_pass";

            int sizeBefore = ctx.getResult().getSteps().size();
            runner().runSteps(children, ctx);
            int sizeAfter = ctx.getResult().getSteps().size();

            int passed = 0, failed = 0, error = 0;
            for (int i = sizeBefore; i < sizeAfter; i++) {
                String s = ctx.getResult().getSteps().get(i).getStatus();
                if ("success".equals(s)) passed++;
                else if ("failed".equals(s)) failed++;
                else if ("error".equals(s)) error++;
            }
            boolean txPassed = switch (rule.toLowerCase()) {
                case "one_pass" -> passed > 0;
                case "ignore" -> true;
                default -> failed == 0 && error == 0;
            };
            sr.setStatus(txPassed ? "success" : "failed");
            if (!txPassed) {
                sr.setError("事务失败 (passed=" + passed + ", failed=" + failed + ", error=" + error + ")");
            }
            sr.getExtracts().add(EngineDtos.ExtractResult.builder()
                    .variable("__tx_passed__").value(String.valueOf(passed)).build());
            sr.getExtracts().add(EngineDtos.ExtractResult.builder()
                    .variable("__tx_failed__").value(String.valueOf(failed)).build());
        } catch (Exception e) {
            sr.setStatus("error");
            sr.setError("TRANSACTION 控制器异常: " + e.getMessage());
        }
        sr.setDurationMs(System.currentTimeMillis() - start);
        return sr;
    }
}