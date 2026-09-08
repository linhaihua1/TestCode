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
                readKvList(step.cfg("headers")),
                readKvList(step.cfg("query")),
                str(step.cfg("body")),
                readAssertions(step.cfg("assertions")),
                readExtracts(step.cfg("extracts")));
        long start = System.currentTimeMillis();
        EngineDtos.StepResult sr = EngineDtos.StepResult.builder()
                .stepName(step.getName())
                .status("success")
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
                sr.setStatus("failed");
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
            sr.setStatus("error");
            sr.setError("HTTP 请求失败: " + e.getMessage());
            sr.setDurationMs(System.currentTimeMillis() - start);
        }
        return sr;
    }

    @SuppressWarnings("unchecked")
    private List<EngineDtos.Assertion> readAssertions(Object raw) {
        List<EngineDtos.Assertion> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
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
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<EngineDtos.Extract> readExtracts(Object raw) {
        List<EngineDtos.Extract> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add(new EngineDtos.Extract(
                            StepExecutor.toStr(m.get("type")),
                            StepExecutor.toStr(m.get("expression")),
                            StepExecutor.toStr(m.get("variable")),
                            StepExecutor.toStr(m.get("defaultValue")),
                            StepExecutor.toStr(m.get("failStrategy"), "ignore")));
                }
            }
        }
        return out;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String str(Object o, String def) { return o == null ? def : String.valueOf(o); }
}