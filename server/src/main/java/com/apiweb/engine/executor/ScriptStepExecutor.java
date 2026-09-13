package com.apiweb.engine.executor;

import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.StepExecutor;
import com.apiweb.engine.step.StepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import com.apiweb.engine.RunStatus;

/**
 * 自定义脚本步骤执行器（{@link StepType#SCRIPT}）。
 *
 * <p>支持 JavaScript,执行上下文注入所有变量,脚本可读写。
 */
@Component
@RequiredArgsConstructor
public class ScriptStepExecutor implements StepExecutor {

    @Override
    public StepType type() { return StepType.SCRIPT; }

    @Override
    public EngineDtos.StepResult execute(StepDef step, StepContext ctx) {
        String language = step.cfg("language");
        if (language == null || language.isBlank() || language.equalsIgnoreCase("javascript") || language.equalsIgnoreCase("js")) {
            return executeJs(step, ctx);
        }
        // 其它语言暂不支持,标记为失败但不中断
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status(RunStatus.FAILED.getCode())
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .error("暂不支持的脚本语言: " + language)
                .build();
        return sr;
    }

    /**
     * 执行 JavaScript 脚本步骤。
     *
     * <p>脚本上下文注入当前全部变量（可读），脚本返回值写入变量 {@code __script_result__}
     * 供后续步骤引用。若当前 JVM 无 JS 引擎（如未引入 GraalVM JS），标记步骤失败而非中断。
     *
     * @param step 脚本步骤定义（config.script 为脚本内容）
     * @param ctx  执行上下文（提供变量池与结果累积）
     * @return 脚本步骤执行结果
     */
    private EngineDtos.StepResult executeJs(StepDef step, StepContext ctx) {
        String script = step.cfg("script");
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status(RunStatus.SUCCESS.getCode())
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .build();
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
            if (engine == null) {
                sr.setStatus(RunStatus.FAILED.getCode());
                sr.setError("当前 JVM 无可用 JS 引擎（建议引入 org.graalvm.polyglot:js 依赖）");
            } else {
                ctx.getResolver().all().forEach(engine::put);
                Object ret = engine.eval(script == null ? "" : script);
                if (ret != null) {
                    String value = String.valueOf(ret);
                    ctx.putVariable("__script_result__", value);
                }
            }
        } catch (Exception e) {
            sr.setStatus(RunStatus.ERROR.getCode());
            sr.setError("脚本执行失败: " + e.getMessage());
        }
        return sr;
    }
}