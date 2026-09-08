package com.apiweb.engine.variable;

import com.apiweb.entity.EnvironmentEntity;
import com.apiweb.entity.GlobalVariableEntity;
import com.apiweb.engine.VariablesResolver;
import com.apiweb.security.AesGcm;
import com.apiweb.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 变量优先级合并器（按 §2 变量优先级：提取 > 用例 > 环境 > 全局）。
 *
 * <h3>优先级顺序（后者覆盖前者）</h3>
 * <ol>
 *   <li>全局变量（{@link GlobalVariableEntity}）：跨项目 / 跨环境共享</li>
 *   <li>环境变量（{@link EnvironmentEntity#variables}）：当前环境 host/variables</li>
 *   <li>用例变量（{@code CaseVariable}）：本用例范围内</li>
 *   <li>提取变量（{@code Map<String,String>}）：执行步骤过程中产生</li>
 * </ol>
 *
 * <p>SECRET 类型变量会在合并前自动解密（仅在执行时,API 返回给前端时不解密）。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   VariableMerger merger = new VariableMerger();
 *   Map<String,String> final = merger.merge(
 *       List.of(globalVarList),
 *       environment,
 *       caseVarList,
 *       extractMap
 *   );
 *   VariablesResolver resolver = new VariablesResolver(final);
 * }</pre>
 */
@Slf4j
@Component
public class VariableMerger {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseVariable {
        private String name;
        private String value;
    }

    public Map<String, String> merge(List<GlobalVariableEntity> globals,
                                      EnvironmentEntity env,
                                      List<CaseVariable> caseVars,
                                      Map<String, String> extracts) {
        Map<String, String> pool = new LinkedHashMap<>();

        // 优先级 1：全局变量（SECRET 自动解密）
        if (globals != null) {
            for (GlobalVariableEntity g : globals) {
                if (g.getName() == null || g.getName().isBlank()) continue;
                String value = g.getValue() == null ? "" : g.getValue();
                if ("SECRET".equalsIgnoreCase(g.getType())) {
                    try {
                        value = AesGcm.decrypt(value);
                    } catch (Exception e) {
                        log.warn("全局变量 [{}] 解密失败,使用空值: {}", g.getName(), e.getMessage());
                        value = "";
                    }
                }
                pool.put(g.getName(), value);
            }
        }

        // 优先级 2：环境（host 注入 baseUrl,variables 注入其它变量）
        if (env != null) {
            if (env.getBaseUrl() != null && !env.getBaseUrl().isBlank()) {
                pool.put("baseUrl", env.getBaseUrl());
            }
            Map<String, String> envVars = parseJsonMap(env.getVariables());
            envVars.forEach((k, v) -> pool.put("env." + k, v));
            Map<String, String> envHeaders = parseJsonMap(env.getHeaders());
            envHeaders.forEach((k, v) -> pool.put("envHeader." + k, v));
        }

        // 优先级 3：用例变量
        if (caseVars != null) {
            for (CaseVariable cv : caseVars) {
                if (cv.getName() == null || cv.getName().isBlank()) continue;
                pool.put(cv.getName(), cv.getValue() == null ? "" : cv.getValue());
            }
        }

        // 优先级 4：提取变量（最高,覆盖所有）
        if (extracts != null) {
            pool.putAll(extracts);
        }

        return pool;
    }

    public VariablesResolver buildResolver(List<GlobalVariableEntity> globals,
                                            EnvironmentEntity env,
                                            List<CaseVariable> caseVars,
                                            Map<String, String> extracts) {
        return new VariablesResolver(merge(globals, env, caseVars, extracts));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseJsonMap(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        try {
            Object parsed = JsonUtils.fromJson(json, Object.class);
            if (parsed instanceof Map<?, ?> m) {
                m.forEach((k, v) -> map.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
            } else if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> kv) {
                        Object key = kv.get("key");
                        Object value = kv.get("value");
                        if (key != null) map.put(String.valueOf(key), value == null ? "" : String.valueOf(value));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}