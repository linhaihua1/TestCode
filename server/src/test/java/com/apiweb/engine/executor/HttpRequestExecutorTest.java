package com.apiweb.engine.executor;

import com.apiweb.engine.AssertEvaluator;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.Extractor;
import com.apiweb.engine.HttpExecutor;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.StepExecutor;
import com.apiweb.engine.VariablesResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HttpRequestExecutor} 的 config 解析契约测试。
 *
 * <p><b>回归背景（严重静默失效）</b>：执行器曾用 {@code step.cfg("assertions")} 取值，
 * 而 {@link StepDef#cfg(String)} 返回 {@code String}（内部 {@code String.valueOf(list)}），
 * 于是 {@code raw instanceof List} 恒为 false ——
 * <b>请求头、Query、断言、变量提取全部被丢弃，用例永远"通过"但实际什么都没校验</b>。
 *
 * <p>本测试直接断言「下游真正收到的内容」，而非只测工具方法，防止同类回归。</p>
 */
class HttpRequestExecutorTest {

    private static final String FLAT_STEP_JSON =
            "[{\"url\":\"http://x/login\",\"name\":\"登录\",\"type\":\"HTTP_REQUEST\",\"method\":\"POST\","
                    + "\"enabled\":true,"
                    + "\"headers\":[{\"key\":\"Content-Type\",\"value\":\"application/json\"}],"
                    + "\"query\":[{\"key\":\"page\",\"value\":\"1\"}],"
                    + "\"extracts\":[{\"type\":\"JSONPATH\",\"variable\":\"loginToken\","
                    + "\"expression\":\"$.data.token\",\"failStrategy\":\"stop\"}],"
                    + "\"position\":\"TEST\","
                    + "\"assertions\":[{\"type\":\"STATUS\",\"source\":\"status\","
                    + "\"expected\":\"200\",\"operator\":\"equals\"}],"
                    + "\"failStrategy\":\"stop\"}]";

    private final HttpExecutor httpExecutor = mock(HttpExecutor.class);
    private final AssertEvaluator assertEvaluator = mock(AssertEvaluator.class);
    private final Extractor extractor = mock(Extractor.class);
    private final HttpRequestExecutor executor =
            new HttpRequestExecutor(httpExecutor, assertEvaluator, extractor);

    private void stubDownstream() throws Exception {
        when(httpExecutor.execute(any(), any())).thenReturn(
                new HttpExecutor.Response(200, Map.of(), "{\"code\":0}", 12L));
        when(assertEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of());
        when(extractor.extract(any(EngineDtos.Extract.class), any(Extractor.Response.class)))
                .thenReturn(EngineDtos.ExtractResult.builder().variable("loginToken").value("tk").build());
    }

    private static StepContext newContext() {
        return new StepContext(new VariablesResolver(new HashMap<>()),
                EngineDtos.ExecutionResult.builder().result("success").build());
    }

    /** 捕获真正传给 HttpExecutor 的 HttpStep */
    private EngineDtos.HttpStep captureHttpStep(StepDef step) throws Exception {
        stubDownstream();
        executor.execute(step, newContext());
        ArgumentCaptor<EngineDtos.HttpStep> captor =
                ArgumentCaptor.forClass(EngineDtos.HttpStep.class);
        verify(httpExecutor).execute(captor.capture(), any());
        return captor.getValue();
    }

    @Test
    @DisplayName("扁平步骤 JSON：headers/query/assertions/extracts 必须完整传到下游")
    void flatStep_shouldPassAllStructuredConfigDownstream() throws Exception {
        StepDef step = StepDef.fromJsonArray(FLAT_STEP_JSON).get(0);

        EngineDtos.HttpStep httpStep = captureHttpStep(step);

        assertEquals("POST", httpStep.getMethod());
        assertEquals("http://x/login", httpStep.getUrl());

        assertEquals(1, httpStep.getHeaders().size(), "请求头被丢弃");
        assertEquals("Content-Type", httpStep.getHeaders().get(0).getKey());
        assertEquals("application/json", httpStep.getHeaders().get(0).getValue());

        assertEquals(1, httpStep.getQuery().size(), "Query 参数被丢弃");
        assertEquals("page", httpStep.getQuery().get(0).getKey());

        assertEquals(1, httpStep.getAssertions().size(), "断言被丢弃（用例会假通过）");
        EngineDtos.Assertion a = httpStep.getAssertions().get(0);
        assertEquals("status", a.getSource());
        assertEquals("equals", a.getOperator());
        assertEquals("200", a.getExpected());

        assertEquals(1, httpStep.getExtracts().size(), "变量提取被丢弃");
        assertEquals("loginToken", httpStep.getExtracts().get(0).getVariable());
        assertEquals("$.data.token", httpStep.getExtracts().get(0).getExpression());
    }

    @Test
    @DisplayName("历史形态：config 中数组为 JSON 字符串时同样解析")
    void jsonStringConfig_shouldAlsoBeParsed() throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("method", "GET");
        config.put("url", "http://x/list");
        config.put("headers", "[{\"key\":\"X-Token\",\"value\":\"t\"}]");
        config.put("query", "[{\"key\":\"page\",\"value\":\"2\"}]");
        config.put("assertions", "[{\"type\":\"STATUS\",\"source\":\"status\",\"expected\":\"200\"}]");
        config.put("extracts", "[{\"type\":\"JSONPATH\",\"variable\":\"firstId\",\"expression\":\"$.data[0].id\"}]");

        StepDef step = new StepDef();
        step.setType("HTTP_REQUEST");
        step.setName("历史步骤");
        step.setConfig(config);

        EngineDtos.HttpStep httpStep = captureHttpStep(step);

        assertEquals(1, httpStep.getHeaders().size());
        assertEquals("X-Token", httpStep.getHeaders().get(0).getKey());
        assertEquals(1, httpStep.getQuery().size());
        assertEquals(1, httpStep.getAssertions().size());
        assertEquals(1, httpStep.getExtracts().size());
        assertEquals("firstId", httpStep.getExtracts().get(0).getVariable());
    }

    @Test
    @DisplayName("cfgRaw 返回原始对象，cfg 仍返回字符串（语义不混用）")
    void cfgRaw_shouldKeepStructuredType() {
        StepDef step = StepDef.fromJsonArray(FLAT_STEP_JSON).get(0);

        assertNotNull(step.cfgRaw("assertions"));
        assertTrue(step.cfgRaw("assertions") instanceof List);
        // cfg 的契约是字符串，执行器不应再用它取结构化字段
        assertTrue(step.cfg("assertions") instanceof String);
    }

    @Test
    @DisplayName("readKvList：null / 非数组 / 坏 JSON 均返回空列表且不抛异常")
    void readKvList_shouldBeResilient() {
        StepExecutor helper = new StepExecutor() {
            @Override
            public com.apiweb.engine.step.StepType type() {
                return com.apiweb.engine.step.StepType.HTTP_REQUEST;
            }

            @Override
            public EngineDtos.StepResult execute(StepDef step, StepContext context) {
                return null;
            }
        };

        assertTrue(helper.readKvList(null).isEmpty());
        assertTrue(helper.readKvList("not-a-json").isEmpty());
        assertTrue(helper.readKvList(Map.of("key", "v")).isEmpty());
        assertEquals(1, helper.readKvList(List.of(Map.of("key", "k", "value", "v"))).size());
        assertEquals(1, helper.readKvList("[{\"key\":\"k\",\"value\":\"v\"}]").size());
    }
}
