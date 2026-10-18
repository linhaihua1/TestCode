package com.apiweb.converter;

import com.apiweb.entity.DebugRecordEntity;
import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ScenarioStepEntity;
import com.apiweb.vo.casee.DebugRecordVO;
import com.apiweb.vo.report.ReportDetailVO;
import com.apiweb.vo.scenario.ScenarioVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 出参 VO 的 JSON 字段结构化契约测试（调试记录 / 报告明细 / 场景步骤）。
 *
 * <p><b>回归背景</b>：项目约定「实体以 JSON 字符串存储的字段，VO 必须声明为 Object
 * 并在 {@code @AfterMapping} 中解析」。这三处曾漏做，接口直接输出 JSON 文本，
 * 前端把它们当数组遍历时逐字符渲染（步骤名/断言全部 undefined）。</p>
 */
class ConverterJsonFieldsTest {

    private final CaseConverter caseConverter = Mappers.getMapper(CaseConverter.class);
    private final ReportConverter reportConverter = ReportConverter.INSTANCE;
    private final ScenarioConverter scenarioConverter = ScenarioConverter.INSTANCE;

    @Test
    @DisplayName("DebugRecordVO：assertionResults / extractedVariables / stepResults 解析为结构化对象")
    void debugRecordVO_shouldParseJsonFields() {
        DebugRecordEntity e = new DebugRecordEntity();
        e.setId("d1");
        e.setAssertionResults("[{\"source\":\"status\",\"passed\":true}]");
        e.setExtractedVariables("{\"token\":\"abc\"}");
        e.setStepResults("[{\"stepName\":\"登录\",\"status\":\"success\"}]");

        DebugRecordVO vo = caseConverter.toDebugRecordVO(e);

        assertInstanceOf(List.class, vo.getAssertionResults());
        assertEquals(1, ((List<?>) vo.getAssertionResults()).size());
        assertEquals("status", ((Map<?, ?>) ((List<?>) vo.getAssertionResults()).get(0)).get("source"));

        assertInstanceOf(Map.class, vo.getExtractedVariables());
        assertEquals("abc", ((Map<?, ?>) vo.getExtractedVariables()).get("token"));

        assertInstanceOf(List.class, vo.getStepResults());
        assertEquals("登录", ((Map<?, ?>) ((List<?>) vo.getStepResults()).get(0)).get("stepName"));
    }

    @Test
    @DisplayName("DebugRecordVO：空/缺失 JSON 退化为空集合，保证前端 .length 可用")
    void debugRecordVO_emptyFields() {
        DebugRecordEntity e = new DebugRecordEntity();
        e.setAssertionResults("[]");
        e.setStepResults(null);

        DebugRecordVO vo = caseConverter.toDebugRecordVO(e);

        assertEquals(List.of(), vo.getAssertionResults());
        assertEquals(List.of(), vo.getStepResults());
        assertEquals(List.of(), vo.getExtractedVariables());
    }

    @Test
    @DisplayName("ReportDetailVO：assertions / extracts 解析为数组")
    void reportDetailVO_shouldParseJsonFields() {
        ReportDetailEntity e = new ReportDetailEntity();
        e.setId("rd1");
        e.setStepName("GET /items");
        e.setAssertions("[{\"source\":\"status\",\"expected\":\"200\",\"passed\":true}]");
        e.setExtracts("[{\"variable\":\"id\",\"value\":\"1\"}]");

        ReportDetailVO vo = reportConverter.toDetailVO(e);

        assertInstanceOf(List.class, vo.getAssertions());
        assertEquals(1, ((List<?>) vo.getAssertions()).size());
        assertEquals("status", ((Map<?, ?>) ((List<?>) vo.getAssertions()).get(0)).get("source"));
        assertInstanceOf(List.class, vo.getExtracts());
        assertEquals("id", ((Map<?, ?>) ((List<?>) vo.getExtracts()).get(0)).get("variable"));
    }

    @Test
    @DisplayName("ScenarioStepVO：assertions / extracts 解析为数组（前端断言编辑器按数组绑定）")
    void scenarioStepVO_shouldParseJsonFields() {
        ScenarioStepEntity e = new ScenarioStepEntity();
        e.setId("ss1");
        e.setName("步骤");
        e.setAssertions("[{\"type\":\"STATUS\",\"expected\":\"200\"}]");
        e.setExtracts("[]");

        ScenarioVO.ScenarioStepVO vo = scenarioConverter.toStepVO(e);

        assertInstanceOf(List.class, vo.getAssertions());
        assertEquals(1, ((List<?>) vo.getAssertions()).size());
        assertInstanceOf(List.class, vo.getExtracts());
        assertEquals(List.of(), vo.getExtracts());
    }

    @Test
    @DisplayName("列表转换同样解析 JSON 字段")
    void listConversion_shouldAlsoParse() {
        DebugRecordEntity a = new DebugRecordEntity();
        a.setStepResults("[{\"stepName\":\"a\"}]");
        DebugRecordEntity b = new DebugRecordEntity();
        b.setStepResults(null);

        List<DebugRecordVO> vos = caseConverter.toDebugRecordVOList(List.of(a, b));

        assertEquals(2, vos.size());
        assertInstanceOf(List.class, vos.get(0).getStepResults());
        assertEquals(1, ((List<?>) vos.get(0).getStepResults()).size());
        assertEquals(List.of(), vos.get(1).getStepResults());
    }
}
