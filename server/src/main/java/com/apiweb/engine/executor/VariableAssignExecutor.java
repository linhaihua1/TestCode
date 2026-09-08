package com.apiweb.engine.executor;

import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.StepExecutor;
import com.apiweb.engine.step.StepType;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;

/**
 * 变量赋值步骤执行器（{@link StepType#VARIABLE_ASSIGN}）。
 *
 * <ul>
 *   <li>literal：直接赋值 {@code value}（支持 {{var}} 占位符）</li>
 *   <li>expression：用 JS 引擎计算 {@code expression}（可用其它变量）</li>
 * </ul>
 */
@Component
public class VariableAssignExecutor implements StepExecutor {

    @Override
    public StepType type() { return StepType.VARIABLE_ASSIGN; }

    @Override
    public EngineDtos.StepResult execute(StepDef step, StepContext ctx) {
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status("success")
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .build();
        try {
            String name = step.cfg("name");
            if (name == null || name.isBlank()) {
                sr.setStatus("failed");
                sr.setError("变量名不能为空");
                return sr;
            }
            String mode = step.cfg("mode");
            String value;
            if ("expression".equalsIgnoreCase(mode)) {
                String expr = step.cfg("expression");
                ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
                if (engine == null) {
                    sr.setStatus("failed");
                    sr.setError("当前 JVM 无 JS 引擎,无法计算 expression");
                    return sr;
                }
                ctx.getResolver().all().forEach(engine::put);
                Object ret = engine.eval(expr == null ? "" : expr);
                value = ret == null ? "" : String.valueOf(ret);
            } else {
                value = ctx.getResolver().resolve(step.cfg("value"));
            }
            ctx.putVariable(name, value);
            sr.setExtracts(java.util.List.of(
                    EngineDtos.ExtractResult.builder().variable(name).value(value).build()));
        } catch (Exception e) {
            sr.setStatus("error");
            sr.setError("变量赋值失败: " + e.getMessage());
        }
        return sr;
    }
}