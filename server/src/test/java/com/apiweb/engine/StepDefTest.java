package com.apiweb.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StepDef 解析单测。
 *
 * <p>回归背景：前端与历史数据都用「扁平步骤」JSON（method/url/script/assertions 等在顶层），
 * 而 {@link StepDef#fromMap} 曾只读 {@code config} 子对象，导致顶层字段被丢弃、
 * HTTP 步骤的 url/method 恒为空、接口用例调试恒失败。此测试锁定解析契约。</p>
 */
class StepDefTest {

    @Test
    @DisplayName("扁平步骤：顶层业务字段应并入 config")
    void flatStep_shouldCollectTopLevelFieldsIntoConfig() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "http");
        raw.put("name", "登录");
        raw.put("position", "TEST");
        raw.put("enabled", true);
        raw.put("method", "POST");
        raw.put("url", "{{baseUrl}}/login");
        raw.put("headers", List.of(Map.of("key", "X", "value", "1")));
        raw.put("assertions", List.of(Map.of("source", "status", "expected", "200")));
        raw.put("_id", "local-ui-id");
        raw.put("__activeTab", "headers");

        StepDef d = StepDef.fromMap(raw);

        assertEquals("HTTP_REQUEST", d.getType());
        assertEquals("POST", d.cfg("method"));
        assertEquals("{{baseUrl}}/login", d.cfg("url"));
        assertNotNull(d.getConfig().get("headers"));
        assertNotNull(d.getConfig().get("assertions"));
        // 身份字段与前端 UI 字段不应污染 config
        assertFalse(d.getConfig().containsKey("_id"));
        assertFalse(d.getConfig().containsKey("__activeTab"));
        assertFalse(d.getConfig().containsKey("name"));
        assertFalse(d.getConfig().containsKey("type"));
    }

    @Test
    @DisplayName("显式 config 子对象优先于顶层同名字段")
    void explicitConfig_shouldWinOverFlatFields() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "HTTP_REQUEST");
        raw.put("url", "flat-url");
        raw.put("config", Map.of("url", "nested-url"));

        StepDef d = StepDef.fromMap(raw);

        assertEquals("nested-url", d.cfg("url"));
    }

    @Test
    @DisplayName("script 步骤的顶层 script/language 应进入 config")
    void scriptStep_shouldCollectScriptIntoConfig() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "SCRIPT");
        raw.put("language", "javascript");
        raw.put("script", "return 1");

        StepDef d = StepDef.fromMap(raw);

        assertEquals("SCRIPT", d.getType());
        assertEquals("javascript", d.cfg("language"));
        assertEquals("return 1", d.cfg("script"));
    }

    @Test
    @DisplayName("children 应递归解析")
    void children_shouldBeParsedRecursively() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "SCRIPT");
        child.put("script", "2");

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "IF");
        raw.put("condition", "1==1");
        raw.put("children", List.of(child));

        StepDef d = StepDef.fromMap(raw);

        assertEquals("IF", d.getType());
        assertEquals("1==1", d.cfg("condition"));
        assertEquals(1, d.getChildren().size());
        assertEquals("SCRIPT", d.getChildren().get(0).getType());
        assertEquals("2", d.getChildren().get(0).cfg("script"));
    }

    @Test
    @DisplayName("混合格式：config 子对象的 name（变量名）与步骤名 name 互不干扰")
    void nestedConfigWithName_shouldNotConflictWithStepName() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "VARIABLE_ASSIGN");
        raw.put("name", "计算 sum");
        raw.put("config", Map.of("mode", "expression", "name", "sum", "expression", "1 + 2"));

        StepDef d = StepDef.fromMap(raw);

        assertEquals("VARIABLE_ASSIGN", d.getType());
        assertEquals("计算 sum", d.getName());
        assertEquals("sum", d.cfg("name"));
    }

    @Test
    @DisplayName("真实 case.steps JSON：断言与提取应被解析出来")
    void realCaseStepsJson_shouldParseAssertionsAndExtracts() {
        String json = "[{\"url\":\"http://x/login\",\"name\":\"s1\",\"type\":\"HTTP_REQUEST\",\"method\":\"POST\","
                + "\"enabled\":true,\"headers\":[{\"key\":\"Content-Type\",\"value\":\"application/json\"}],"
                + "\"extracts\":[{\"type\":\"JSONPATH\",\"variable\":\"loginToken\",\"expression\":\"$.data.token\",\"failStrategy\":\"stop\"}],"
                + "\"position\":\"TEST\",\"assertions\":[{\"type\":\"STATUS\",\"expected\":\"200\",\"operator\":\"equals\"}],"
                + "\"failStrategy\":\"stop\"}]";
        List<StepDef> steps = StepDef.fromJsonArray(json);
        assertEquals(1, steps.size());
        // 结构化字段必须用 cfgRaw 取原始对象（cfg() 会字符串化，执行器据此判断 List 会恒为空）
        Object a = steps.get(0).cfgRaw("assertions");
        Object e = steps.get(0).cfgRaw("extracts");
        assertTrue(a instanceof List, "assertions class=" + (a == null ? "null" : a.getClass().getName()));
        assertTrue(e instanceof List, "extracts class=" + (e == null ? "null" : e.getClass().getName()));
        assertEquals(1, ((List<?>) a).size());
        assertEquals(1, ((List<?>) e).size());
    }

    @Test
    @DisplayName("fromJsonArray：坏 JSON 返回空列表而不抛异常")
    void fromJsonArray_shouldBeResilient() {
        assertTrue(StepDef.fromJsonArray(null).isEmpty());
        assertTrue(StepDef.fromJsonArray("not-a-json").isEmpty());
        assertEquals(1, StepDef.fromJsonArray("[{\"type\":\"http\"}]").size());
    }
}
