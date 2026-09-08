package com.apiweb.report;

import com.apiweb.common.BizException;
import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.mapper.ReportDetailMapper;
import com.apiweb.mapper.ReportMapper;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报告导出服务：HTML（轻量）/ PDF（OpenHTMLToPDF）。
 *
 * <h3>HTML 导出</h3>
 * 自包含 HTML + 内联 CSS,可直接在浏览器打开,无外部依赖。
 *
 * <h3>PDF 导出</h3>
 * 基于 OpenHTMLToPDF（若 classpath 中没有,则降级为 HTML）。
 *
 * <p>生产建议：把渲染放到 worker 线程,避免大报告阻塞请求线程。
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final ReportMapper reportMapper;
    private final ReportDetailMapper reportDetailMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * 生成自包含 HTML。
     */
    public String renderHtml(String reportId) {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null) throw BizException.notFound("报告不存在");
        List<ReportDetailEntity> details = reportDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReportDetailEntity>()
                        .eq(ReportDetailEntity::getReportId, reportId));

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">")
          .append("<title>").append(escape(report.getName())).append("</title>")
          .append("<style>")
          .append(":root{--ok:#52c41a;--fail:#f5222d;--err:#fa8c16;--skipped:#8c8c8c;}")
          .append("body{font-family:-apple-system,'PingFang SC',sans-serif;color:#222;padding:24px;max-width:1200px;margin:0 auto;background:#fafafa;}")
          .append("h1{margin:0 0 8px;font-size:24px;}h2{font-size:18px;border-bottom:1px solid #eee;padding-bottom:6px;margin-top:32px;}")
          .append(".meta{color:#666;font-size:14px;margin-bottom:16px;}.meta span{margin-right:24px;}")
          .append(".stats{display:flex;gap:16px;margin:16px 0;flex-wrap:wrap;}")
          .append(".stat{background:#fff;border:1px solid #eee;border-radius:6px;padding:12px 18px;min-width:120px;}")
          .append(".stat .v{font-size:24px;font-weight:600;}.stat .l{font-size:12px;color:#666;margin-top:4px;}")
          .append(".ok{color:var(--ok);}.fail{color:var(--fail);}.err{color:var(--err);}.skipped{color:var(--skipped);}")
          .append("table{width:100%;border-collapse:collapse;background:#fff;}")
          .append("th,td{padding:8px 12px;border:1px solid #eee;text-align:left;font-size:13px;}")
          .append("th{background:#fafafa;font-weight:600;}")
          .append("pre{background:#f6f6f6;padding:8px;border-radius:4px;overflow-x:auto;font-size:12px;margin:4px 0;}")
          .append(".pill{display:inline-block;padding:2px 8px;border-radius:10px;font-size:12px;color:#fff;}")
          .append(".pill.success{background:var(--ok);}.pill.failed{background:var(--fail);}.pill.error{background:var(--err);}.pill.skipped{background:var(--skipped);}")
          .append("</style></head><body>");

        sb.append("<h1>").append(escape(report.getName())).append("</h1>");
        sb.append("<div class=\"meta\">")
          .append("<span>📋 报告ID：").append(escape(report.getId())).append("</span>")
          .append("<span>⏱ 启动时间：").append(DTF.format(report.getStartedAt())).append("</span>");
        if (report.getFinishedAt() != null) {
            sb.append("<span>🏁 结束时间：").append(DTF.format(report.getFinishedAt())).append("</span>");
        }
        sb.append("<span>⌛ 耗时：").append(report.getDuration()).append(" ms</span>")
          .append("<span>📨 触发：").append(escape(report.getTriggerType())).append("</span>");
        if (report.getTriggerBy() != null) {
            sb.append("<span>👤 by ").append(escape(report.getTriggerBy())).append("</span>");
        }
        sb.append("</div>");

        // 汇总卡片
        sb.append("<div class=\"stats\">");
        sb.append(stat("total", report.getTotalCases(), "用例总数"));
        sb.append(stat("ok", report.getPassedCases(), "通过"));
        sb.append(stat("fail", report.getFailedCases(), "失败"));
        sb.append(stat("err", report.getErrorCases(), "异常"));
        sb.append(stat("skipped", report.getSkippedCases(), "跳过"));
        sb.append(stat("total", report.getTotalAssertions(), "断言总数"));
        sb.append(stat("ok", report.getPassedAssertions(), "断言通过"));
        sb.append(stat("fail", report.getFailedAssertions(), "断言失败"));
        if (report.getAvgResponseTime() != null && report.getAvgResponseTime() > 0) {
            sb.append(stat("total", report.getAvgResponseTime(), "平均响应 ms"));
        }
        if (report.getP95ResponseTime() != null && report.getP95ResponseTime() > 0) {
            sb.append(stat("total", report.getP95ResponseTime(), "P95 响应 ms"));
        }
        sb.append("</div>");

        // 明细表
        sb.append("<h2>步骤明细（").append(details.size()).append(" 条）</h2>");
        sb.append("<table><thead><tr>")
          .append("<th>#</th><th>状态</th><th>步骤名</th><th>断言</th><th>提取</th><th>错误</th>")
          .append("</tr></thead><tbody>");
        int idx = 0;
        for (ReportDetailEntity d : details) {
            sb.append("<tr>")
              .append("<td>").append(++idx).append("</td>")
              .append("<td><span class=\"pill ").append(escape(d.getStatus())).append("\">")
              .append(escape(d.getStatus())).append("</span></td>")
              .append("<td>").append(escape(d.getStepName())).append("</td>")
              .append("<td>").append(formatJson(d.getAssertions())).append("</td>")
              .append("<td>").append(formatJson(d.getExtracts())).append("</td>")
              .append("<td>").append(d.getError() == null ? "" : "<pre>" + escape(d.getError()) + "</pre>").append("</td>")
              .append("</tr>");
        }
        sb.append("</tbody></table>");
        sb.append("<p style=\"margin-top:32px;color:#999;font-size:12px;\">Generated by codex-apiweb at ")
          .append(DTF.format(Instant.now()))
          .append("</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * 生成 PDF 二进制（依赖 OpenHTMLToPDF,缺失时回退为 HTML,Content-Type 由 controller 决定）。
     */
    public byte[] renderPdf(String reportId) {
        String html = renderHtml(reportId);
        try {
            // 尝试加载 OpenHTMLToPDF
            Class<?> openPdfClass = Class.forName("com.openhtmltopdf.pdfboxout.PdfRendererBuilder");
            Object builder = openPdfClass.getDeclaredConstructor().newInstance();
            openPdfClass.getMethod("withHtmlContent", String.class, java.net.URI.class)
                    .invoke(builder, html, null);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            openPdfClass.getMethod("toStream", java.io.OutputStream.class).invoke(builder, baos);
            openPdfClass.getMethod("run").invoke(builder);
            return baos.toByteArray();
        } catch (ClassNotFoundException e) {
            // 没有 OpenHTMLToPDF,降级为 HTML（前端可自行另存为 PDF）
            return html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BizException.badRequest("PDF 渲染失败: " + e.getMessage());
        }
    }

    private static String stat(String cls, Integer v, String label) {
        int value = v == null ? 0 : v;
        return "<div class=\"stat\"><div class=\"v " + cls + "\">" + value + "</div>"
                + "<div class=\"l\">" + label + "</div></div>";
    }

    @SuppressWarnings("unchecked")
    private static String formatJson(String json) {
        if (json == null || json.isBlank()) return "—";
        try {
            Object parsed = JsonUtils.fromJson(json, Object.class);
            if (parsed instanceof List<?> list && !list.isEmpty()) {
                StringBuilder sb = new StringBuilder("<pre>");
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        if (m.containsKey("name") && m.containsKey("value")) {
                            // 断言 / 提取项
                            sb.append(escape(String.valueOf(m.get("name")))).append(" = ");
                            Object v = m.get("value");
                            Object pass = m.get("passed");
                            if (pass instanceof Boolean b) {
                                sb.append(b ? "✓ " : "✗ ");
                            }
                            sb.append(escape(String.valueOf(v == null ? "" : v))).append("\n");
                        } else {
                            sb.append(escape(JsonUtils.toJson(item))).append("\n");
                        }
                    } else {
                        sb.append(escape(String.valueOf(item))).append("\n");
                    }
                }
                sb.append("</pre>");
                return sb.toString();
            }
            return "<pre>" + escape(JsonUtils.toJson(parsed)) + "</pre>";
        } catch (Exception e) {
            return "<pre>" + escape(json) + "</pre>";
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 一次性列出报告全部明细（前端轮询用,减少请求次数）。
     */
    public List<ReportDetailEntity> details(String reportId) {
        return reportDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReportDetailEntity>()
                        .eq(ReportDetailEntity::getReportId, reportId));
    }

    /**
     * 按 reportId 列表收集实体（前端批量展示）。
     */
    public List<ReportEntity> byIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
            .distinct()
            .map(reportMapper::selectById)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }
}