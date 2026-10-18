package com.apiweb.engine.executor;

import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.HttpExecutor;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.StepExecutor;
import com.apiweb.engine.AssertEvaluator;
import com.apiweb.engine.Extractor;
import com.apiweb.engine.step.AssertionType;
import com.apiweb.engine.step.ExtractType;
import com.apiweb.engine.step.StepType;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.apiweb.engine.RunStatus;

/**
 * HTTP 请求步骤执行器（{@link StepType#HTTP_REQUEST}）。
 *
 * <p>从 config 读取 method/url/headers/query/body/assertions/extracts,
 * 发送到上游 → 评估断言 → 提取变量 → 写报告。
 */
@Component
@RequiredArgsConstructor
public class HttpRequestExecutor implements StepExecutor {

    private final HttpExecutor httpExecutor;
    private final AssertEvaluator assertEvaluator;
    private final Extractor extractor;

    @Override
    public StepType type() { return StepType.HTTP_REQUEST; }

    @Override
    public EngineDtos.StepResult execute(StepDef step, StepContext ctx) {
        EngineDtos.HttpStep httpStep = new EngineDtos.HttpStep(
                step.getName(),
                str(step.cfg("method"), "GET"),
                str(step.cfg("url"), ""),
                // 结构化字段必须取原始对象：cfg() 会把 List 字符串化成非 JSON 文本，
                // 导致 headers/query/assertions/extracts 被静默丢弃
                readKvList(step.cfgRaw("headers")),
                readKvList(step.cfgRaw("query")),
                str(step.cfg("body")),
                readAssertions(step.cfgRaw("assertions")),
                readExtracts(step.cfgRaw("extracts")));
        long start = System.currentTimeMillis();
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status(RunStatus.SUCCESS.getCode())
                .assertions(new ArrayList<>())
                .extracts(new ArrayList<>())
                .build();
        try {
            HttpExecutor.Response resp = httpExecutor.execute(httpStep, ctx.getResolver());
            sr.setDurationMs(resp.durationMs());

            // 1. 断言
            List<EngineDtos.AssertionResult> assertionResults =
                    assertEvaluator.evaluateAll(httpStep.getAssertions(),
                            new AssertEvaluator.EvalInput(resp.status(),
                                    HttpExecutor.lowercaseHeaders(resp.headers()),
                                    resp.body(), resp.durationMs()));
            sr.setAssertions(assertionResults);
            if (assertionResults.stream().anyMatch(a -> !a.isPassed())) {
                sr.setStatus(RunStatus.FAILED.getCode());
            }

            // 2. 提取
            for (EngineDtos.Extract e : httpStep.getExtracts()) {
                EngineDtos.ExtractResult er = extractor.extract(e,
                        new Extractor.Response(resp.status(),
                                HttpExecutor.lowercaseHeaders(resp.headers()), resp.body()));
                sr.getExtracts().add(er);
                ctx.putVariable(er.getVariable(), er.getValue());
            }

            // 3. 摘要
            sr.setRequestSummary(JsonUtils.toJson(Map.of(
                    "method", httpStep.getMethod(),
                    "url", ctx.getResolver().resolve(httpStep.getUrl()))));
            String bodyPreview = resp.body() == null ? "" :
                    (resp.body().length() > 1024 ? resp.body().substring(0, 1024) + "..." : resp.body());
            sr.setResponseSummary(JsonUtils.toJson(Map.of(
                    "status", resp.status(),
                    "durationMs", resp.durationMs(),
                    "body", bodyPreview)));
        } catch (Exception e) {
            sr.setStatus(RunStatus.ERROR.getCode());
            sr.setError("HTTP 请求失败: " + e.getMessage());
            sr.setDurationMs(System.currentTimeMillis() - start);
        }
        return sr;
    }

    /**
     * 从步骤 config 的 assertions 字段解析断言列表。
     *
     * <p>每个元素需是含 type/source/property/operator/expected/message 的 Map；
     * 缺失字段用默认值（operator 默认 equals）补齐，保证后续评估不抛 NPE。
     *
     * @param raw 步骤 config 中的 assertions 字段（List 或 JSON 数组字符串）
     * @return 解析后的断言列表（非数组时返回空列表）
     */
    private List<EngineDtos.Assertion> readAssertions(Object raw) {
        List<EngineDtos.Assertion> out = new ArrayList<>();
        for (Object item : asList(raw)) {
            if (item instanceof Map<?, ?> m) {
                out.add(new EngineDtos.Assertion(
                        StepExecutor.toStr(m.get("type")),
                        StepExecutor.toStr(m.get("source")),
                        StepExecutor.toStr(m.get("property")),
                        StepExecutor.toStr(m.get("operator"), "equals"),
                        m.get("expected") == null ? null : String.valueOf(m.get("expected")),
                        StepExecutor.toStr(m.get("message"))));
            }
        }
        return out;
    }

    /**
     * 从步骤 config 的 extracts 字段解析变量提取列表。
     *
     * @param raw 步骤 config 中的 extracts 字段（List 或 JSON 数组字符串）
     * @return 解析后的提取列表（非数组时返回空列表）
     */
    private List<EngineDtos.Extract> readExtracts(Object raw) {
        List<EngineDtos.Extract> out = new ArrayList<>();
        for (Object item : asList(raw)) {
            if (item instanceof Map<?, ?> m) {
                out.add(new EngineDtos.Extract(
                        StepExecutor.toStr(m.get("type")),
                        StepExecutor.toStr(m.get("expression")),
                        StepExecutor.toStr(m.get("variable")),
                        StepExecutor.toStr(m.get("defaultValue")),
                        StepExecutor.toStr(m.get("failStrategy"), "ignore")));
            }
        }
        return out;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String str(Object o, String def) { return o == null ? def : String.valueOf(o); }
}