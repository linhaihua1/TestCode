package com.apiweb.converter;

import com.apiweb.entity.PerfReportEntity;
import com.apiweb.vo.perf.PerfReportVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 性能测试报告 JSON 字段出参契约测试。
 *
 * <p>锁死历史缺陷：{@link PerfReportVO} 的 summary/series/labels/errors 曾为 String，
 * 前端报告详情页按对象/数组使用（{@code series.map(...)}、{@code :data-source="labels"}），
 * 点击「查看」后直接抛错并整页白屏。此处确保 MapStruct 转换后输出结构化对象。</p>
 */
class PerfConverterTest {

    private final PerfConverter converter = Mappers.getMapper(PerfConverter.class);

    private static PerfReportEntity reportOf(String summary, String series, String labels, String errors) {
        PerfReportEntity e = new PerfReportEntity();
        e.setId("r1");
        e.setProjectId("p1");
        e.setName("压测报告");
        e.setStatus("success");
        e.setSummary(summary);
        e.setSeries(series);
        e.setLabels(labels);
        e.setErrors(errors);
        return e;
    }

    @Test
    @DisplayName("summary: JSON 对象字符串解析为 Map（前端读 summary.avg）")
    void reportVO_summaryParsedAsMap() {
        PerfReportVO vo = converter.toReportVO(reportOf(
                "{\"sampleCount\":10,\"errorCount\":2,\"avg\":1579.0,\"p90\":2918,\"tps\":0.9258}",
                "[]", "[]", "[]"));

        assertInstanceOf(Map.class, vo.getSummary());
        Map<?, ?> summary = (Map<?, ?>) vo.getSummary();
        assertEquals(10, summary.get("sampleCount"));
        assertEquals(2, summary.get("errorCount"));
        assertNotNull(summary.get("avg"));
    }

    @Test
    @DisplayName("series/labels/errors: JSON 数组字符串解析为 List（前端 series.map / data-source）")
    void reportVO_listFieldsParsedAsList() {
        PerfReportVO vo = converter.toReportVO(reportOf(
                "{}",
                "[{\"ts\":1.5,\"elapsed\":320},{\"ts\":2.0,\"elapsed\":410}]",
                "[{\"label\":\"GET /api/items\",\"count\":10,\"avg\":1579.0}]",
                "[{\"message\":\"HTTP 500\",\"count\":3}]"));

        assertInstanceOf(List.class, vo.getSeries());
        assertInstanceOf(List.class, vo.getLabels());
        assertInstanceOf(List.class, vo.getErrors());

        List<?> series = (List<?>) vo.getSeries();
        assertEquals(2, series.size());
        assertEquals(320, ((Map<?, ?>) series.get(0)).get("elapsed"));

        assertEquals(1, ((List<?>) vo.getLabels()).size());
        assertEquals("HTTP 500",
                ((Map<?, ?>) ((List<?>) vo.getErrors()).get(0)).get("message"));
    }

    @Test
    @DisplayName("pending 报告的空占位值（{} / []）解析为空 Map / 空 List，不抛错")
    void reportVO_emptyPlaceholders() {
        PerfReportVO vo = converter.toReportVO(reportOf("{}", "[]", "[]", "[]"));

        // "{}" → Map（空），"[]" → List（空）
        assertInstanceOf(Map.class, vo.getSummary());
        assertTrue(((Map<?, ?>) vo.getSummary()).isEmpty());
        assertEquals(List.of(), vo.getSeries());
        assertEquals(List.of(), vo.getLabels());
        assertEquals(List.of(), vo.getErrors());
    }

    @Test
    @DisplayName("null / 空白 JSON 字段退化为空集合，保证前端 .length 可用")
    void reportVO_nullFields() {
        PerfReportVO vo = converter.toReportVO(reportOf(null, "  ", null, null));

        assertNotNull(vo.getSeries());
        assertNotNull(vo.getLabels());
        assertNotNull(vo.getErrors());
    }

    @Test
    @DisplayName("报告列表转换同样解析 JSON 字段")
    void reportVOList_parsed() {
        PerfReportEntity a = reportOf("{\"sampleCount\":5}", "[{\"ts\":1,\"elapsed\":10}]", "[]", "[]");
        PerfReportEntity b = reportOf("{}", "[]", "[]", "[]");
        b.setId("r2");

        List<PerfReportVO> vos = converter.toReportVOList(List.of(a, b));

        assertEquals(2, vos.size());
        assertInstanceOf(Map.class, vos.get(0).getSummary());
        assertEquals(1, ((List<?>) vos.get(0).getSeries()).size());
        assertInstanceOf(Map.class, vos.get(1).getSummary());
    }
}
