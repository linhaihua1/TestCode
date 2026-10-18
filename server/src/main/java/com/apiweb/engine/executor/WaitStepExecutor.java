package com.apiweb.engine.executor;

import com.apiweb.config.EngineProperties;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.StepExecutor;
import com.apiweb.engine.step.StepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import com.apiweb.engine.RunStatus;

/**
 * 等待步骤执行器（{@link StepType#WAIT}）。
 *
 * <ul>
 *   <li>fixed：固定等待 {@code ms} 毫秒</li>
 *   <li>dynamic：条件等待,每 {@code intervalMs} 检查 {@code condition},直到满足或超时</li>
 * </ul>
 *
 * <p>缺省时长取自 {@link EngineProperties}（{@code apiweb.engine.*}）。
 */
@Component
@RequiredArgsConstructor
public class WaitStepExecutor implements StepExecutor {

    private final EngineProperties engine;

    @Override
    public StepType type() { return StepType.WAIT; }

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
            String mode = step.cfg("mode");
            if (mode == null || mode.isBlank() || "fixed".equalsIgnoreCase(mode)) {
                // 前端字段为 waitMs，兼容历史数据里的 ms；均缺失时用引擎默认值
                Long waitMs = step.cfgLong("waitMs");
                if (waitMs == null) waitMs = step.cfgLong("ms");
                long ms = waitMs == null ? engine.getWaitDefaultMs() : waitMs;
                if (ms > 0) Thread.sleep(ms);
            } else if ("dynamic".equalsIgnoreCase(mode)) {
                long intervalMs = step.cfgLong("intervalMs") == null
                        ? engine.getWaitDynamicIntervalMs() : step.cfgLong("intervalMs");
                long timeoutMs = step.cfgLong("timeoutMs") == null
                        ? engine.getWaitDynamicTimeoutMs() : step.cfgLong("timeoutMs");
                String condition = step.cfg("condition");
                long deadline = System.currentTimeMillis() + timeoutMs;
                boolean ok = false;
                while (System.currentTimeMillis() < deadline) {
                    if (condition == null || condition.isBlank()) {
                        ok = true; break;
                    }
                    String resolved = ctx.getResolver().resolve(condition);
                    if (!"false".equalsIgnoreCase(resolved)) {
                        ok = true; break;
                    }
                    Thread.sleep(intervalMs);
                }
                if (!ok && condition != null && !condition.isBlank()) {
                    sr.setStatus(RunStatus.FAILED.getCode());
                    sr.setError("动态等待超时（" + timeoutMs + "ms）");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sr.setStatus(RunStatus.ERROR.getCode());
            sr.setError("等待被中断");
        } catch (Exception e) {
            sr.setStatus(RunStatus.ERROR.getCode());
            sr.setError("等待失败: " + e.getMessage());
        }
        sr.setDurationMs(System.currentTimeMillis() - start);
        return sr;
    }
}