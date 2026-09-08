package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.mapper.ReportDetailMapper;
import com.apiweb.mapper.ReportMapper;
import com.apiweb.report.ReportExportService;
import com.apiweb.report.TrendService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 测试报告：列表 / 明细 / 导出（HTML/PDF）/ 趋势统计。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportMapper reportMapper;
    private final ReportDetailMapper reportDetailMapper;
    private final ReportExportService exportService;
    private final TrendService trendService;

    @GetMapping
    public Result<List<ReportEntity>> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String scenarioId,
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<ReportEntity> result = reportMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<ReportEntity>()
                        .eq(projectId != null && !projectId.isBlank(),
                                ReportEntity::getProjectId, projectId)
                        .eq(scenarioId != null && !scenarioId.isBlank(),
                                ReportEntity::getScenarioId, scenarioId)
                        .eq(taskId != null && !taskId.isBlank(),
                                ReportEntity::getTaskId, taskId)
                        .orderByDesc(ReportEntity::getStartedAt));
        return Result.ok(result.getRecords());
    }

    @GetMapping("/{id}")
    public Result<ReportEntity> get(@PathVariable String id) {
        ReportEntity r = reportMapper.selectById(id);
        if (r == null) throw BizException.notFound("报告不存在");
        return Result.ok(r);
    }

    @GetMapping("/{id}/details")
    public Result<List<ReportDetailEntity>> detail(@PathVariable String id) {
        if (reportMapper.selectById(id) == null) {
            throw BizException.notFound("报告不存在");
        }
        return Result.ok(reportDetailMapper.selectList(
                new LambdaQueryWrapper<ReportDetailEntity>()
                        .eq(ReportDetailEntity::getReportId, id)));
    }

    @AuditLog(action = "export_html", entityType = "report")
    @GetMapping("/{id}/export/html")
    public ResponseEntity<String> exportHtml(@PathVariable String id) {
        String html = exportService.renderHtml(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.setContentDispositionFormData("attachment",
                "report-" + id + ".html");
        return new ResponseEntity<>(html, headers, 200);
    }

    @AuditLog(action = "export_pdf", entityType = "report")
    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable String id) {
        byte[] pdf = exportService.renderPdf(id);
        HttpHeaders headers = new HttpHeaders();
        // PDF 模式成功时是 application/pdf,降级为 HTML 时是 text/html
        boolean isPdf = pdf.length > 4 && pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F';
        if (isPdf) {
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "report-" + id + ".pdf");
        } else {
            headers.setContentType(MediaType.TEXT_HTML);
            headers.setContentDispositionFormData("attachment", "report-" + id + ".html");
        }
        return new ResponseEntity<>(pdf, headers, 200);
    }

    /**
     * 趋势统计：最近 N 天每日通过率 / 用例数 / 失败数 / 平均响应时间。
     */
    @GetMapping("/trend")
    public Result<List<TrendService.TrendBucket>> trend(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(trendService.compute(projectId, days));
    }

    /**
     * 整体汇总（顶栏卡片）。
     */
    @GetMapping("/summary")
    public Result<TrendService.Summary> summary(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(trendService.summary(projectId, days));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        reportDetailMapper.delete(new LambdaQueryWrapper<ReportDetailEntity>()
                .eq(ReportDetailEntity::getReportId, id));
        reportMapper.deleteById(id);
        return Result.ok();
    }
}