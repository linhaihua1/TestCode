package com.apiweb.engine;

import com.apiweb.engine.step.StepType;
import com.apiweb.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一步骤定义（按需求文档 §4.2 支持 10 种步骤类型）。
 *
 * <p>兼容旧的 {@code Map<String,Object>} 步骤结构,内部统一用 {@link StepDef} 描述。
 * 字段命名沿用前端 JSON 字段：type / name / config / children。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepDef {

    /** 步骤 id（数据库） */
    private String id;
    /** 步骤类型 */
    private String type;
    /** 步骤名称 */
    private String name;
    /** 位置：PRE/TEST/POST */
    private String position;
    /** 是否启用（false = 跳过） */
    private Boolean enabled;
    /** 失败策略：stop / continue */
    private String failStrategy;
    /** 备注 */
    private String remark;
    /** 类型相关配置(JSON object),按 type 不同 schema */
    private Map<String, Object> config;
    /** 子步骤（控制器专用） */
    private List<StepDef> children;

    /**
     * 从 Map 解析（兼容旧的 step JSON）。
     */
    @SuppressWarnings("unchecked")
    public static StepDef fromMap(Map<String, Object> raw) {
        StepDef d = new StepDef();
        d.id = str(raw.get("id"));
        d.type = StepType.parse(str(raw.get("type"), "HTTP_REQUEST")).name();
        d.name = str(raw.get("name"), "未命名步骤");
        d.position = str(raw.get("position"), "TEST");
        d.enabled = raw.get("enabled") instanceof Boolean b ? b : true;
        d.failStrategy = str(raw.get("failStrategy"), "stop");
        d.remark = str(raw.get("remark"));
        Object cfg = raw.get("config");
        if (cfg instanceof Map<?, ?> m) {
            d.config = (Map<String, Object>) m;
        } else if (cfg instanceof String s && !s.isBlank()) {
            try {
                d.config = JsonUtils.fromJson(s, Map.class);
            } catch (Exception e) {
                d.config = new LinkedHashMap<>();
            }
        } else {
            d.config = new LinkedHashMap<>();
        }
        Object ch = raw.get("children");
        if (ch instanceof List<?> list) {
            d.children = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    d.children.add(fromMap((Map<String, Object>) m));
                }
            }
        }
        return d;
    }

    /** 从 JSON 数组字符串解析步骤列表 */
    public static List<StepDef> fromJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> list = JsonUtils.fromJson(json, List.class);
            if (list == null) return List.of();
            List<StepDef> out = new ArrayList<>(list.size());
            for (Map<String, Object> m : list) {
                out.add(fromMap(m));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 从 config 取字符串字段 */
    public String cfg(String key) {
        if (config == null) return null;
        Object v = config.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** 从 config 取整数字段 */
    public Integer cfgInt(String key) {
        if (config == null) return null;
        Object v = config.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 config 取长整型字段 */
    public Long cfgLong(String key) {
        if (config == null) return null;
        Object v = config.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 config 取布尔字段 */
    public Boolean cfgBool(String key) {
        if (config == null) return null;
        Object v = config.get(key);
        if (v instanceof Boolean b) return b;
        if (v == null) return null;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String str(Object o, String def) { return o == null ? def : String.valueOf(o); }
}