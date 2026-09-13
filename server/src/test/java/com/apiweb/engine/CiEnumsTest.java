package com.apiweb.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI/CD 枚举契约测试。
 *
 * <p>流水线类型、执行方式、目标类型、运行状态、门禁指标与操作符的 {@code code}
 * 会落库并直接传给前端，属于稳定契约；本测试锁死取值与解析语义，防止重构改坏协议。
 */
class CiEnumsTest {

    // ---------------- PipelineTestType ----------------

    @Test
    @DisplayName("PipelineTestType: 编码与前后端契约一致")
    void testTypeCodes() {
        assertEquals("api", PipelineTestType.API.getCode());
        assertEquals("ui", PipelineTestType.UI.getCode());
        assertEquals("perf", PipelineTestType.PERF.getCode());
        assertUniqueCodes(PipelineTestType.values());
    }

    @Test
    @DisplayName("PipelineTestType: 解析大小写不敏感，非法值走默认值")
    void testTypeParse() {
        assertSame(PipelineTestType.API, PipelineTestType.parse("api"));
        assertSame(PipelineTestType.PERF, PipelineTestType.parse("PERF"));
        assertNull(PipelineTestType.parse("security"));
        assertSame(PipelineTestType.API, PipelineTestType.parseOrDefault(null));
    }

    // ---------------- PipelineExecMode ----------------

    @Test
    @DisplayName("PipelineExecMode: local 与 jenkins 两种执行方式")
    void execMode() {
        assertEquals("local", PipelineExecMode.LOCAL.getCode());
        assertEquals("jenkins", PipelineExecMode.JENKINS.getCode());
        assertSame(PipelineExecMode.JENKINS, PipelineExecMode.parse("jenkins"));
        assertSame(PipelineExecMode.LOCAL, PipelineExecMode.parseOrDefault("unknown"));
    }

    // ---------------- PipelineTargetType ----------------

    @Test
    @DisplayName("PipelineTargetType: 按测试类型推导默认目标")
    void targetTypeDefault() {
        assertSame(PipelineTargetType.TEST_TASK, PipelineTargetType.defaultOf(PipelineTestType.API));
        assertSame(PipelineTargetType.UI_SCENARIO, PipelineTargetType.defaultOf(PipelineTestType.UI));
        assertSame(PipelineTargetType.PERF_CASE, PipelineTargetType.defaultOf(PipelineTestType.PERF));
        assertSame(PipelineTargetType.TEST_TASK, PipelineTargetType.defaultOf(null));
        assertSame(PipelineTargetType.PERF_CASE, PipelineTargetType.parse("perf_case"));
        assertSame(PipelineTargetType.TEST_TASK, PipelineTargetType.parseOrDefault("nope"));
    }

    // ---------------- PipelineRunStatus ----------------

    @Test
    @DisplayName("PipelineRunStatus: 终态与失败态判定")
    void runStatus() {
        assertFalse(PipelineRunStatus.PENDING.isTerminal());
        assertFalse(PipelineRunStatus.RUNNING.isTerminal());
        assertTrue(PipelineRunStatus.SUCCESS.isTerminal());
        assertTrue(PipelineRunStatus.FAILED.isTerminal());
        assertTrue(PipelineRunStatus.ERROR.isTerminal());

        assertFalse(PipelineRunStatus.SUCCESS.isFailure());
        assertTrue(PipelineRunStatus.FAILED.isFailure());
        assertTrue(PipelineRunStatus.ERROR.isFailure());

        assertSame(PipelineRunStatus.RUNNING, PipelineRunStatus.parse("running"));
        assertNull(PipelineRunStatus.parse("canceled"));
    }

    // ---------------- QualityGateMetric / Operator ----------------

    @Test
    @DisplayName("QualityGateMetric: 编码与解析")
    void gateMetric() {
        assertEquals("pass_rate", QualityGateMetric.PASS_RATE.getCode());
        assertEquals("fail_rate", QualityGateMetric.FAIL_RATE.getCode());
        assertEquals("p95_response_time", QualityGateMetric.P95_RESPONSE_TIME.getCode());
        assertSame(QualityGateMetric.TPS, QualityGateMetric.parse("tps"));
        assertNull(QualityGateMetric.parse("not-exist"));
        // 展示标签需带上单位，避免前端再次拼接字面量
        assertTrue(QualityGateMetric.AVG_RESPONSE_TIME.getLabelWithUnit().endsWith("(ms)"));
    }

    @Test
    @DisplayName("QualityGateOperator: 命中即违规（含等于与边界）")
    void gateOperator() {
        assertTrue(QualityGateOperator.GT.violate(6, 5));
        assertFalse(QualityGateOperator.GT.violate(5, 5));
        assertTrue(QualityGateOperator.GTE.violate(5, 5));
        assertTrue(QualityGateOperator.LT.violate(90, 95));
        assertFalse(QualityGateOperator.LT.violate(100, 95));
        assertTrue(QualityGateOperator.LTE.violate(95, 95));
        assertTrue(QualityGateOperator.EQ.violate(3, 3));
        assertFalse(QualityGateOperator.EQ.violate(3, 4));
        assertSame(QualityGateOperator.GT, QualityGateOperator.parseOrDefault("nonsense"));
    }

    // ---------------- JenkinsBuildResult ----------------

    @Test
    @DisplayName("JenkinsBuildResult: 构建结果映射为流水线状态")
    void jenkinsResult() {
        assertTrue(JenkinsBuildResult.SUCCESS.isFinished());
        assertTrue(JenkinsBuildResult.UNSTABLE.isFinished());
        assertFalse(JenkinsBuildResult.RUNNING.isFinished());

        assertEquals(PipelineRunStatus.SUCCESS, JenkinsBuildResult.SUCCESS.toRunStatus());
        assertEquals(PipelineRunStatus.FAILED, JenkinsBuildResult.FAILURE.toRunStatus());
        assertEquals(PipelineRunStatus.FAILED, JenkinsBuildResult.UNSTABLE.toRunStatus());
        assertEquals(PipelineRunStatus.RUNNING, JenkinsBuildResult.RUNNING.toRunStatus());
        assertEquals(PipelineRunStatus.ERROR, JenkinsBuildResult.NOT_FOUND.toRunStatus());

        // Jenkins 构建中返回 result=null，需解析为 RUNNING 而不是 UNKNOWN
        assertSame(JenkinsBuildResult.RUNNING, JenkinsBuildResult.parse(null));
        assertSame(JenkinsBuildResult.UNSTABLE, JenkinsBuildResult.parse("unstable"));
        assertSame(JenkinsBuildResult.UNKNOWN, JenkinsBuildResult.parse("whatever"));
    }

    // ---------------- TriggerType ----------------

    @Test
    @DisplayName("TriggerType: 支持 jenkins 触发来源")
    void triggerType() {
        assertSame(TriggerType.JENKINS, TriggerType.parse("jenkins"));
        assertEquals("jenkins", TriggerType.JENKINS.getCode());
        assertSame(TriggerType.WEBHOOK, TriggerType.parse("webhook"));
    }

    /** 断言一组枚举的 code 不重复（契约值唯一） */
    private void assertUniqueCodes(Object[] values) {
        Set<String> codes = new HashSet<>();
        Arrays.stream(values).forEach(v -> {
            try {
                codes.add((String) v.getClass().getMethod("getCode").invoke(v));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(values.length, codes.size(), "枚举编码必须唯一");
    }
}
