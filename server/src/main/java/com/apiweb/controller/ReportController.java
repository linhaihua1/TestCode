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

    /**
     * 分页查询测试报告列表（按开始时间倒序）。
     * <p>支持按项目/场景/任务过滤；单页上限 100，防止大页查询。
     *
     * @param projectId  项目 ID（可选）
     * @param scenarioId 场景 ID（可选）
     * @param taskId     任务 ID（可选）
     * @param page       页码，默认 1
     * @param size       每页条数，默认 20，上限 100
     * @return 当前页报告列表
     */
    @GetMapping
    public Result<List<ReportEntity>> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String scenarioId,
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        // 单页上限 100，防止超大 size 拖垮查询
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

    /**
     * 获取报告详情。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 报告实体（含汇总指标、状态等）
     * @throws BizException 报告不存在时抛 notFound
     */
    @GetMapping("/{id}")
    public Result<ReportEntity> get(@PathVariable String id) {
        ReportEntity r = reportMapper.selectById(id);
        if (r == null) throw BizException.notFound("报告不存在");
        return Result.ok(r);
    }

    /**
     * 获取某报告的用例级执行明细列表（每个用例/步骤的执行结果）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 报告明细列表
     * @throws BizException 报告不存在时抛 notFound
     */
    @GetMapping("/{id}/details")
    public Result<List<ReportDetailEntity>> detail(@PathVariable String id) {
        if (reportMapper.selectById(id) == null) {
            throw BizException.notFound("报告不存在");
        }
        return Result.ok(reportDetailMapper.selectList(
                new LambdaQueryWrapper<ReportDetailEntity>()
                        .eq(ReportDetailEntity::getReportId, id)));
    }

    /**
     * 导出报告为 HTML 文件（下载附件）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return HTML 文本响应（Content-Disposition 触发浏览器下载）
     * @audit 写审计日志：action=export_html, entityType=report
     */
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

    /**
     * 导出报告为 PDF；若渲染服务降级为 HTML，则响应类型与扩展名相应变化（通过魔数 %PDF 判断实际格式）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 文件响应（application/pdf 或 text/html 附件）
     * @audit 写审计日志：action=export_pdf, entityType=report
     */
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
     *
     * @param projectId 项目 ID（可选，为空则统计全部项目）
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
     * 整体汇总（顶栏卡片）：最近 N 天的通过率、用例数、失败数、平均响应时间等聚合指标。
     *
     * @param projectId 项目 ID（可选，为空则统计全部项目）
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
     * 删除报告：先删用例级明细再删主表（物理删除，避免外键残留）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 无业务数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        reportDetailMapper.delete(new LambdaQueryWrapper<ReportDetailEntity>()
                .eq(ReportDetailEntity::getReportId, id));
        reportMapper.deleteById(id);
        return Result.ok();
    }
}