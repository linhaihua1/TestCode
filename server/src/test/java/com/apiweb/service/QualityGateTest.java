package com.apiweb.service;

import com.apiweb.entity.CiPipelineEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 质量门禁判定测试。
 *
 * <p>门禁是 CI/CD 能不能「卡住不合格构建」的关键：规则命中即判定违规，
 * 流水线状态随之变成 FAILED。这里覆盖规则命中/未命中、边界值、未知指标与空规则等场景。
 */
class QualityGateTest {

    @Test
    @DisplayName("无门禁规则：返回空明细，视为通过")
    void noRules() {
        List<Map<String, Object>> result = CiExecutionService.evaluateGate(pipeline("[]"), Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("规则命中（失败率 > 5%）：判定违规")
    void failRateViolated() {
        String gate = "[{\"metric\":\"fail_rate\",\"operator\":\"gt\",\"threshold\":5}]";
        Map<String, Object> summary = Map.of("fail_rate", 10.0);
        List<Map<String, Object>> result = CiExecutionService.evaluateGate(pipeline(gate), summary);

        assertEquals(1, result.size());
        assertFalse((Boolean) result.get(0).get("passed"));
        assertEquals(10.0, ((Number) result.get(0).get("actual")).doubleValue());
        assertEquals("失败率(%)", result.get(0).get("metricLabel"));
        assertEquals(">", result.get(0).get("operatorSymbol"));
    }

    @Test
    @DisplayName("规则未命中（失败率 0% ≤ 5%）：判定通过")
    void failRatePassed() {
        String gate = "[{\"metric\":\"fail_rate\",\"operator\":\"gt\",\"threshold\":5}]";
        List<Map<String, Object>> result =
                CiExecutionService.evaluateGate(pipeline(gate), Map.of("fail_rate", 0.0));
        assertTrue((Boolean) result.get(0).get("passed"));
    }

    @Test
    @DisplayName("通过率门禁：低于阈值才违规（注意方向）")
    void passRateDirection() {
        String gate = "[{\"metric\":\"pass_rate\",\"operator\":\"lt\",\"threshold\":95}]";
        // 通过率 100%：不违规
        assertTrue((Boolean) CiExecutionService.evaluateGate(pipeline(gate), Map.of("pass_rate", 100.0))
                .get(0).get("passed"));
        // 通过率 90%：违规
        assertFalse((Boolean) CiExecutionService.evaluateGate(pipeline(gate), Map.of("pass_rate", 90.0))
                .get(0).get("passed"));
    }

    @Test
    @DisplayName("响应时间门禁：P95 超过 2000ms 违规")
    void p95Gate() {
        String gate = "[{\"metric\":\"p95_response_time\",\"operator\":\"gt\",\"threshold\":2000}]";
        assertTrue((Boolean) CiExecutionService.evaluateGate(pipeline(gate), Map.of("p95_response_time", 1500.0))
                .get(0).get("passed"));
        assertFalse((Boolean) CiExecutionService.evaluateGate(pipeline(gate), Map.of("p95_response_time", 2500.0))
                .get(0).get("passed"));
    }

    @Test
    @DisplayName("指标缺失时按 0 处理，未知指标被忽略")
    void missingAndUnknownMetric() {
        String gate = "[{\"metric\":\"error_rate\",\"operator\":\"gt\",\"threshold\":0},"
                + "{\"metric\":\"unknown_metric\",\"operator\":\"gt\",\"threshold\":1}]";
        List<Map<String, Object>> result = CiExecutionService.evaluateGate(pipeline(gate), Map.of());
        // 未知指标被跳过，只保留可识别的规则
        assertEquals(1, result.size());
        assertEquals("error_rate", result.get(0).get("metric"));
        // 缺失指标按 0 处理：0 > 0 不成立 → 通过
        assertTrue((Boolean) result.get(0).get("passed"));
    }

    @Test
    @DisplayName("多条规则：任一条违规即整体不通过")
    void multipleRules() {
        String gate = "[{\"metric\":\"fail_rate\",\"operator\":\"gt\",\"threshold\":5},"
                + "{\"metric\":\"avg_response_time\",\"operator\":\"gt\",\"threshold\":800}]";
        Map<String, Object> summary = Map.of("fail_rate", 0.0, "avg_response_time", 1200.0);
        List<Map<String, Object>> result = CiExecutionService.evaluateGate(pipeline(gate), summary);

        assertEquals(2, result.size());
        assertTrue((Boolean) result.get(0).get("passed"));
        assertFalse((Boolean) result.get(1).get("passed"));
        // 调用方用 allMatch(passed) 聚合，此处模拟该语义
        assertFalse(result.stream().allMatch(r -> Boolean.TRUE.equals(r.get("passed"))));
    }

    /** 构造仅含门禁配置的流水线（判定方法只依赖 qualityGate） */
    private static CiPipelineEntity pipeline(String qualityGate) {
        CiPipelineEntity p = new CiPipelineEntity();
        p.setQualityGate(qualityGate);
        return p;
    }
}
