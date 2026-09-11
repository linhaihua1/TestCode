package com.apiweb.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 宽容的 JSON 字符串反序列化器。
 *
 * <p>后端大量实体字段（variables / headers / steps / tags 等）用 {@code String} 存 JSON，
 * 而前端以结构化类型（数组 / 对象）提交。默认 Jackson 会把数组反序列化到 String 字段时报
 * {@code Cannot deserialize value of type String from Array value}。
 *
 * <p>本反序列化器兼容三种输入：
 * <ul>
 *   <li>JSON 字符串（如 {@code "[]"}、{@code "{\"a\":1}"}）：原样返回</li>
 *   <li>JSON 数组 / 对象（前端提交的结构化数据）：序列化为 JSON 字符串返回</li>
 *   <li>null：返回 null</li>
 * </ul>
 *
 * <p>使用方式：在实体 String 字段上标注 {@code @JsonDeserialize(using = JsonStringDeserializer.class)}。
 */
public class JsonStringDeserializer extends JsonDeserializer<String> {

    private static final ObjectMapper MAPPER = JsonUtils.MAPPER;

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.getCurrentToken();
        if (token == null) {
            token = p.nextToken();
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_STRING) {
            return p.getText();
        }
        // 数组 / 对象 / 数字 / 布尔等结构化值：读成通用树再序列化回 JSON 字符串
        Object value = MAPPER.readValue(p, Object.class);
        return MAPPER.writeValueAsString(value);
    }
}
