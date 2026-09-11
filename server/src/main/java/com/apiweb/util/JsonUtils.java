package com.apiweb.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.util.Map;

/**
 * JSON 工具：统一 ObjectMapper，兼容 Instant（UTC）。
 */
public final class JsonUtils {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {}

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    public static List<Map<String, Object>> toList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return fromJson(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * 宽容解析：把 JSON 字符串解析为通用对象（List/Map/String/Number）。
     *
     * <p>用于处理结构未知或混合类型的 JSON 字段（如 tags 是字符串数组、steps 是对象数组），
     * 避免 {@link #toList(String)} 强转 {@code List<Map<String,Object>>} 时对字符串数组抛异常。
     */
    public static Object parseObject(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return fromJson(json, new TypeReference<Map<String, Object>>() {});
    }
}
