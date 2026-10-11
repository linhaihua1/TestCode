package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.PageResult;
import com.apiweb.common.Result;
import com.apiweb.report.ReportExportService;
import com.apiweb.report.TrendService;
import com.apiweb.service.ReportService;
import com.apiweb.vo.report.ReportDetailVO;
import com.apiweb.vo.report.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试报告：列表 / 明细 / 导出（HTML/PDF）/ 趋势统计。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService exportService;
    private final TrendService trendService;

    /**
     * 分页查询测试报告列表（按开始时间倒序）。
     *
     * @param projectId  项目 ID（可选）
     * @param scenarioId 场景 ID（可选）
     * @param taskId     任务 ID（可选）
     * @param page       页码，默认 1
     * @param size       每页条数，默认 20，上限 100
     * @return 分页报告列表（records/total/page/size）
     */
    @GetMapping
    public Result<PageResult<ReportVO>> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String scenarioId,
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(reportService.page(projectId, scenarioId, taskId, page, size));
    }

    /**
     * 获取报告详情。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 报告 VO（含汇总指标、状态等）
     */
    @GetMapping("/{id}")
    public Result<ReportVO> get(@PathVariable String id) {
        return Result.ok(reportService.get(id));
    }

    /**
     * 获取某报告的用例级执行明细列表。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 报告明细列表
     */
    @GetMapping("/{id}/details")
    public Result<List<ReportDetailVO>> detail(@PathVariable String id) {
        return Result.ok(reportService.details(id));
    }

    /**
     * 导出报告为 HTML 文件（下载附件）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return HTML 文本响应
     * @audit 写审计日志：action=export_html, entityType=report
     */
    @AuditLog(action = "export_html", entityType = "report")
    @GetMapping("/{id}/export/html")
    public ResponseEntity<String> exportHtml(@PathVariable String id) {
        String html = exportService.renderHtml(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.setContentDispositionFormData("attachment", "report-" + id + ".html");
        return new ResponseEntity<>(html, headers, 200);
    }

    /**
     * 导出报告为 PDF；若渲染服务降级为 HTML，则响应类型与扩展名相应变化。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 文件响应
     * @audit 写审计日志：action=export_pdf, entityType=report
     */
    @AuditLog(action = "export_pdf", entityType = "report")
    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable String id) {
        byte[] pdf = exportService.renderPdf(id);
        HttpHeaders headers = new HttpHeaders();
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
     *
     * @param projectId 项目 ID（可选）
     * @param days      统计天数，默认 30
     * @return 按天聚合的趋势数据点列表
     */
    @GetMapping("/trend")
    public Result<List<TrendService.TrendBucket>> trend(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(trendService.compute(projectId, days));
    }

    /**
     * 整体汇总（顶栏卡片）。
     *
     * @param projectId 项目 ID（可选）
     * @param days      统计天数，默认 30
     * @return 汇总指标对象
     */
    @GetMapping("/summary")
    public Result<TrendService.Summary> summary(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(trendService.summary(projectId, days));
    }

    /**
     * 删除报告：先删用例级明细再删主表。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 无业务数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        reportService.delete(id);
        return Result.ok();
    }
}
