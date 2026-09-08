package com.apiweb.engine;

import com.apiweb.engine.step.StepType;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.mapper.CaseStepMapper;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.*;

/**
 * 用例执行器（按需求文档 §4.2 重构：10 种步骤类型 + 6 种控制器）。
 *
 * <h3>架构</h3>
 * 每个步骤类型由 {@link StepExecutor} 实现,通过 {@link StepExecutorRegistry} 路由。
 * 控制器（IF/FOR/WHILE/TRANSACTION/ONCE/REF_PUBLIC_CASE）通过递归 {@link #runSteps} 调用子步骤。
 *
 * <h3>输入方式</h3>
 * <ul>
 *   <li>{@link #runCaseSteps(String, VariablesResolver)}：从数据库读 CaseStep[] 执行</li>
 *   <li>{@link #run(String, VariablesResolver)}：旧 API（接受 steps JSON 字符串）</li>
 *   <li>{@link #runSteps(List, StepContext)}：递归入口（控制器内部使用）</li>
 * </ul>
 *
 * <h3>边界保护</h3>
 * <ul>
 *   <li>WHILE 控制器最大迭代 + 超时（由 WhileStepExecutor 实现）</li>
 *   <li>REF_PUBLIC_CASE 嵌套深度上限 5（由 RefPublicCaseExecutor 实现）</li>
 *   <li>JS 引擎无依赖时降级（不阻断用例）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseRunner {

    private final HttpExecutor httpExecutor;
    private final AssertEvaluator assertEvaluator;
    private final Extractor extractor;
    private final StepExecutorRegistry registry;
    private final CaseStepMapper caseStepMapper;

    @PostConstruct
    public void init() {
        // 调试日志
        log.info("CaseRunner 已就绪,共注册 {} 种步骤执行器", registry != null ? "?" : "0");
    }

    // ============================================================
    // 新版入口（接受 StepDef[]）
    // ============================================================

    /**
     * 加载用例的所有步骤（含嵌套 children）并执行。
     *
     * <p>步骤按 parentId/sortOrder 还原树形结构：parentId=null 为顶层,然后按 position 排序。
     */
    public EngineDtos.ExecutionResult runCaseSteps(String caseId, VariablesResolver resolver) {
        EngineDtos.ExecutionResult result = EngineDtos.ExecutionResult.builder()
                .result("success")
                .build();
        StepContext ctx = new StepContext(resolver, result);
        try {
            List<StepDef> rootSteps = loadCaseSteps(caseId);
            runSteps(rootSteps, ctx);
            if (result.getSteps().stream().anyMatch(s -> "error".equals(s.getStatus()))) {
                result.setResult("error");
            } else if (result.getSteps().stream().anyMatch(s -> "failed".equals(s.getStatus()))) {
                result.setResult("failed");
            }
        } catch (Exception e) {
            log.error("用例执行异常", e);
            result.setResult("error");
            result.setErrorLog(e.getMessage());
        }
        result.setTotalDurationMs(System.currentTimeMillis() - ctx.getStartMillis());
        return result;
    }

    /**
     * 从数据库加载指定用例的全部步骤（按 parentId/sortOrder 还原父子关系）。
     *
     * <p>被 RefPublicCaseExecutor 调用。
     */
    public List<StepDef> loadCaseSteps(String caseId) {
        if (caseId == null || caseId.isBlank()) return List.of();
        List<CaseStepEntity> all = caseStepMapper.selectList(
                new LambdaQueryWrapper<CaseStepEntity>()
                        .eq(CaseStepEntity::getCaseId, caseId)
                        .orderByAsc(CaseStepEntity::getPosition, CaseStepEntity::getSortOrder));
        if (all.isEmpty()) return List.of();
        // 一次构建所有 StepDef
        Map<String, StepDef> defs = new LinkedHashMap<>();
        for (CaseStepEntity e : all) {
            defs.put(e.getId(), toStepDef(e));
        }
        // 还原父子关系
        List<StepDef> roots = new ArrayList<>();
        for (CaseStepEntity e : all) {
            StepDef def = defs.get(e.getId());
            if (e.getParentId() == null || e.getParentId().isBlank()) {
                roots.add(def);
            } else {
                StepDef parent = defs.get(e.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                    parent.getChildren().add(def);
                }
            }
        }
        return roots;
    }

    /**
     * 递归执行步骤列表（供控制器内部调用）。
     */
    public void runSteps(List<StepDef> steps, StepContext ctx) {
        if (steps == null) return;
        for (StepDef step : steps) {
            try {
                if (Boolean.FALSE.equals(step.getEnabled())) {
                    EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                            .stepName(step.getName())
                            .status("skipped")
                            .assertions(new ArrayList<>())
                            .extracts(new ArrayList<>())
                            .error("步骤已禁用")
                            .build();
                    ctx.getResult().getSteps().add(sr);
                    continue;
                }
                StepType type = StepType.parse(step.getType());
                StepExecutor executor = registry.get(type);
                EngineDtos.StepResult sr = executor.execute(step, ctx);
                ctx.getResult().getSteps().add(sr);
                // 失败策略：stop 时立即中断（除非是 skipped 或 controller 类容器）
                if (("failed".equals(sr.getStatus()) || "error".equals(sr.getStatus()))
                        && !"continue".equalsIgnoreCase(step.getFailStrategy())
                        && !type.isContainer()) {
                    log.warn("步骤 [{}] 失败,中断后续步骤", step.getName());
                    break;
                }
            } catch (Exception e) {
                log.error("步骤 [{}] 执行异常", step.getName(), e);
                EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                        .stepName(step.getName())
                        .status("error")
                        .assertions(new ArrayList<>())
                        .extracts(new ArrayList<>())
                        .error(e.getMessage())
                        .build();
                ctx.getResult().getSteps().add(sr);
            }
        }
    }

    /**
     * 条件求值（供 IF/WHILE/DYNAMIC_WAIT 调用）。
     *
     * <p>策略：JS 引擎优先,无引擎时降级为 "var op value" 简单解析。
     */
    public boolean evalCondition(String condition, VariablesResolver resolver) {
        if (condition == null || condition.isBlank()) return true;
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
            if (engine != null) {
                resolver.all().forEach(engine::put);
                Object ret = engine.eval(condition);
                return Boolean.TRUE.equals(ret);
            }
        } catch (Exception ignored) {
        }
        String c = condition.trim();
        if ("true".equalsIgnoreCase(c)) return true;
        if ("false".equalsIgnoreCase(c)) return false;
        for (String op : new String[]{"==", "!=", ">=", "<=", ">", "<"}) {
            int idx = c.indexOf(op);
            if (idx > 0) {
                String left = resolver.resolve(c.substring(0, idx).trim()
                        .replace("{{", "").replace("}}", ""));
                String right = c.substring(idx + op.length()).trim().replace("\"", "");
                return switch (op) {
                    case "==" -> left.equals(right);
                    case "!=" -> !left.equals(right);
                    case ">" -> toD(left) > toD(right);
                    case "<" -> toD(left) < toD(right);
                    case ">=" -> toD(left) >= toD(right);
                    default -> toD(left) <= toD(right);
                };
            }
        }
        return false;
    }

    private double toD(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    // ============================================================
    // 旧版入口（兼容旧 steps JSON 字段）—— 内部委托到新结构
    // ============================================================

    /**
     * 旧 API：接受 steps JSON 字符串。
     *
     * <p>仍在使用：
     * <ul>
     *   <li>{@link com.apiweb.controller.CaseController#debug}</li>
     *   <li>{@link com.apiweb.engine.CaseRunnerTest}</li>
     * </ul>
     */
    public EngineDtos.ExecutionResult run(String stepsJson, VariablesResolver resolver) {
        EngineDtos.ExecutionResult result = EngineDtos.ExecutionResult.builder()
                .result("success")
                .build();
        StepContext ctx = new StepContext(resolver, result);
        try {
            List<StepDef> steps = StepDef.fromJsonArray(stepsJson);
            runSteps(steps, ctx);
            if (result.getSteps().stream().anyMatch(s -> "error".equals(s.getStatus()))) {
                result.setResult("error");
            } else if (result.getSteps().stream().anyMatch(s -> "failed".equals(s.getStatus()))) {
                result.setResult("failed");
            }
        } catch (Exception e) {
            log.error("用例执行异常", e);
            result.setResult("error");
            result.setErrorLog(e.getMessage());
        }
        result.setTotalDurationMs(System.currentTimeMillis() - ctx.getStartMillis());
        return result;
    }

    // ============================================================
    // 工具
    // ============================================================

    private StepDef toStepDef(CaseStepEntity e) {
        StepDef def = new StepDef();
        def.setId(e.getId());
        def.setType(e.getStepType() == null ? "HTTP_REQUEST" : e.getStepType());
        def.setName(e.getName());
        def.setPosition(e.getPosition());
        def.setEnabled(e.getEnabled() == null ? Boolean.TRUE : e.getEnabled());
        def.setFailStrategy(e.getFailStrategy());
        def.setRemark(e.getRemark());
        if (e.getConfig() != null && !e.getConfig().isBlank()) {
            try {
                def.setConfig(JsonUtils.fromJson(e.getConfig(), Map.class));
            } catch (Exception ex) {
                def.setConfig(new LinkedHashMap<>());
            }
        } else {
            def.setConfig(new LinkedHashMap<>());
        }
        def.setChildren(new ArrayList<>());
        return def;
    }

    // ============================================================
    // 旧版 JSON 步骤解析兼容方法（保留,让旧步骤 JSON 仍能跑）
    // ============================================================

    @SuppressWarnings("unchecked")
    private static List<EngineDtos.KV> toKVList(Object o) {
        List<EngineDtos.KV> list = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    list.add(new EngineDtos.KV(
                            str(m.get("key")),
                            str(m.get("value")),
                            m.get("enabled") instanceof Boolean b ? b : true));
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<EngineDtos.Assertion> toAssertionList(Object o) {
        List<EngineDtos.Assertion> list = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    list.add(new EngineDtos.Assertion(
                            str(m.get("type")),
                            str(m.get("source")),
                            str(m.get("property")),
                            str(m.get("operator"), "equals"),
                            m.get("expected") == null ? null : String.valueOf(m.get("expected")),
                            str(m.get("message"))));
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<EngineDtos.Extract> toExtractList(Object o) {
        List<EngineDtos.Extract> list = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    list.add(new EngineDtos.Extract(
                            str(m.get("type")),
                            str(m.get("expression")),
                            str(m.get("variable")),
                            str(m.get("defaultValue")),
                            str(m.get("failStrategy"), "ignore")));
                }
            }
        }
        return list;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String str(Object o, String def) { return o == null ? def : String.valueOf(o); }
}