package com.apiweb.converter;

import com.apiweb.dto.ui.UiScenarioReq;
import com.apiweb.dto.ui.UiTestCaseReq;
import com.apiweb.entity.UiReportEntity;
import com.apiweb.entity.UiScenarioEntity;
import com.apiweb.entity.UiTestCaseEntity;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.ui.UiReportVO;
import com.apiweb.vo.ui.UiScenarioVO;
import com.apiweb.vo.ui.UiTestCaseVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI 模块 JSON 字段双向契约测试。
 *
 * <p>锁死两类历史缺陷，防止回归：</p>
 * <ol>
 *   <li><b>入参</b>：前端以结构化 JSON（数组/对象）提交 variables / steps，
 *       DTO 侧必须能宽容接收并落库为 JSON 字符串（{@link com.apiweb.util.JsonStringDeserializer}）。
 *       曾因 {@code UiScenarioReq.variables} 声明为 {@code Map} 而前端固定传数组，
 *       导致新建 UI 场景接口直接返回 1001。</li>
 *   <li><b>出参</b>：实体中以 JSON 字符串存储的字段，VO 必须解析为结构化对象，
 *       否则前端按数组使用（{@code details.filter(...)}）会运行时报错。</li>
 * </ol>
 */
class UiConverterTest {

    private final UiConverter converter = Mappers.getMapper(UiConverter.class);

    // ---------------- 入参：DTO 反序列化 ----------------

    @Test
    @DisplayName("UiScenarioReq.variables: 数组入参序列化为 JSON 字符串（前端默认 []）")
    void scenarioReqVariables_acceptsArray() {
        UiScenarioReq req = JsonUtils.fromJson(
                "{\"projectId\":\"p1\",\"name\":\"s\",\"variables\":[]}", UiScenarioReq.class);
        assertEquals("[]", req.getVariables());

        UiScenarioReq req2 = JsonUtils.fromJson(
                "{\"variables\":[{\"key\":\"host\",\"value\":\"https://x\"}]}", UiScenarioReq.class);
        assertEquals("[{\"key\":\"host\",\"value\":\"https://x\"}]", req2.getVariables());
    }

    @Test
    @DisplayName("UiScenarioReq.variables: 对象入参同样兼容")
    void scenarioReqVariables_acceptsObject() {
        UiScenarioReq req = JsonUtils.fromJson("{\"variables\":{\"host\":\"x\"}}",
                UiScenarioReq.class);
        assertEquals("{\"host\":\"x\"}", req.getVariables());
    }

    @Test
    @DisplayName("UiScenarioReq.variables: 缺失/显式 null 时为 null")
    void scenarioReqVariables_absent() {
        assertNull(JsonUtils.fromJson("{\"name\":\"s\"}", UiScenarioReq.class).getVariables());
        assertNull(JsonUtils.fromJson("{\"variables\":null}", UiScenarioReq.class).getVariables());
    }

    @Test
    @DisplayName("UiTestCaseReq.steps: 三段步骤数组入参均可入")
    void testCaseReqSteps_acceptsArray() {
        UiTestCaseReq req = JsonUtils.fromJson(
                "{\"name\":\"c\",\"setupSteps\":[],\"steps\":[{\"action\":\"click\"}],"
                        + "\"teardownSteps\":[]}", UiTestCaseReq.class);
        assertEquals("[]", req.getSetupSteps());
        assertEquals("[{\"action\":\"click\"}]", req.getSteps());
        assertEquals("[]", req.getTeardownSteps());
    }

    // ---------------- 出参：VO 结构化 ----------------

    @Test
    @DisplayName("UiReportVO.details: JSON 数组字符串解析为 List（前端按数组使用）")
    void reportVO_detailsParsedAsList() {
        UiReportEntity entity = new UiReportEntity();
        entity.setId("r1");
        entity.setStatus("success");
        entity.setDetails("[{\"segment\":\"test\",\"status\":\"success\",\"durationMs\":12}]");

        UiReportVO vo = converter.toReportVO(entity);

        assertInstanceOf(List.class, vo.getDetails());
        List<?> details = (List<?>) vo.getDetails();
        assertEquals(1, details.size());
        assertEquals("test", ((Map<?, ?>) details.get(0)).get("segment"));
    }

    @Test
    @DisplayName("UiReportVO.details: 空/异常明细不抛错")
    void reportVO_detailsEdgeCases() {
        UiReportEntity empty = new UiReportEntity();
        empty.setDetails("[]");
        assertEquals(List.of(), converter.toReportVO(empty).getDetails());

        UiReportEntity nil = new UiReportEntity();
        assertEquals(List.of(), converter.toReportVO(nil).getDetails());

        UiReportEntity err = new UiReportEntity();
        err.setDetails("[{\"status\":\"error\",\"message\":\"boom\"}]");
        assertEquals(1, ((List<?>) converter.toReportVO(err).getDetails()).size());

        UiReportEntity obj = new UiReportEntity();
        obj.setDetails("{\"error\":\"x\"}");
        assertInstanceOf(Map.class, converter.toReportVO(obj).getDetails());
    }

    @Test
    @DisplayName("UiReportVO 列表转换同样解析 details")
    void reportVOList_detailsParsed() {
        UiReportEntity a = new UiReportEntity();
        a.setDetails("[{\"status\":\"success\"}]");
        UiReportEntity b = new UiReportEntity();
        b.setDetails("[{\"status\":\"failed\"},{\"status\":\"failed\"}]");

        List<UiReportVO> vos = converter.toReportVOList(List.of(a, b));

        assertEquals(2, vos.size());
        assertInstanceOf(List.class, vos.get(0).getDetails());
        assertEquals(2, ((List<?>) vos.get(1).getDetails()).size());
    }

    @Test
    @DisplayName("UiTestCaseVO: 三段步骤 JSON 字符串解析为结构化对象")
    void testCaseVO_stepsParsed() {
        UiTestCaseEntity entity = new UiTestCaseEntity();
        entity.setId("t1");
        entity.setSetupSteps("[]");
        entity.setSteps("[{\"action\":\"open\",\"value\":\"https://x\"}]");
        entity.setTeardownSteps(null);

        UiTestCaseVO vo = converter.toTestCaseVO(entity);

        assertEquals(List.of(), vo.getSetupSteps());
        assertInstanceOf(List.class, vo.getSteps());
        assertEquals(1, ((List<?>) vo.getSteps()).size());
        // null/空 → 空数组，保证前端 steps.length 可用
        assertEquals(List.of(), vo.getTeardownSteps());
    }

    @Test
    @DisplayName("UiScenarioVO.variables: JSON 字符串解析为结构化对象")
    void scenarioVO_variablesParsed() {
        UiScenarioEntity entity = new UiScenarioEntity();
        entity.setId("s1");
        entity.setVariables("[{\"key\":\"host\",\"value\":\"https://x\"}]");

        UiScenarioVO vo = converter.toScenarioVO(entity);

        assertInstanceOf(List.class, vo.getVariables());
        assertEquals(1, ((List<?>) vo.getVariables()).size());
    }

    @Test
    @DisplayName("round-trip: DTO 入参 JSON 字符串可直接落库并被 VO 解析回数组")
    void roundTrip() {
        UiScenarioReq req = JsonUtils.fromJson(
                "{\"variables\":[{\"key\":\"a\",\"value\":\"1\"}]}", UiScenarioReq.class);

        UiScenarioEntity entity = new UiScenarioEntity();
        entity.setId("s2");
        entity.setVariables(req.getVariables());

        assertInstanceOf(List.class, converter.toScenarioVO(entity).getVariables());
        assertTrue(((List<?>) converter.toScenarioVO(entity).getVariables()).size() == 1);
    }
}
