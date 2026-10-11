package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.dto.perf.PerfCaseReq;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.service.PerfService;
import com.apiweb.vo.perf.PerfCaseVO;
import com.apiweb.vo.perf.PerfReportVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * 性能测试（JMeter）：用例管理 + 异步执行 + 报告。
 */
@RestController
@RequestMapping("/api/v1/perf")
@RequiredArgsConstructor
public class PerfController {

    private final PerfService perfService;

    // ---------------- 用例 ----------------

    /**
     * 查询项目的性能用例列表（仅未删除，按更新时间倒序）。
     */
    @GetMapping("/cases")
    public Result<List<PerfCaseVO>> listCases(@RequestParam String projectId) {
        return Result.ok(perfService.listCases(projectId));
    }

    /**
     * 获取性能用例详情。
     */
    @GetMapping("/cases/{id}")
    public Result<PerfCaseVO> getCase(@PathVariable String id) {
        return Result.ok(perfService.getCase(id));
    }

    /**
     * 新建性能用例。
     *
     * @param req 性能用例请求 DTO，name 必填
     * @return 已落库的用例 VO
     * @audit 写审计日志：action=create, entityType=perf_case
     */
    @AuditLog(action = "create", entityType = "perf_case")
    @PostMapping("/cases")
    public Result<PerfCaseVO> createCase(@Valid @RequestBody PerfCaseReq req) {
        return Result.ok(perfService.createCase(req));
    }

    /**
     * 更新性能用例。
     *
     * @param id  用例 ID（路径参数，必填）
     * @param req 性能用例请求 DTO
     * @return 无业务数据
     * @audit 写审计日志：action=update, entityType=perf_case
     */
    @AuditLog(action = "update", entityType = "perf_case")
    @PutMapping("/cases/{id}")
    public Result<Void> updateCase(@PathVariable String id, @Valid @RequestBody PerfCaseReq req) {
        perfService.updateCase(id, req);
        return Result.ok();
    }

    /**
     * 软删除性能用例。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=perf_case
     */
    @AuditLog(action = "delete", entityType = "perf_case")
    @DeleteMapping("/cases/{id}")
    public Result<Void> deleteCase(@PathVariable String id) {
        perfService.deleteCase(id);
        return Result.ok();
    }

    // ---------------- JMX 导入 / 导出 ----------------

    /**
     * 导出用例为标准 JMeter .jmx 文件（下载）。
     */
    @GetMapping("/cases/{id}/export-jmx")
    public ResponseEntity<byte[]> exportJmx(@PathVariable String id) {
        PerfCaseEntity c = perfService.getCaseEntity(id);
        String jmx = perfService.exportJmx(id);
        byte[] bytes = jmx.getBytes(StandardCharsets.UTF_8);
        String filename = (c.getName() == null ? "perf-case" : c.getName()) + ".jmx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /**
     * 从上传的 .jmx 文件解析为性能用例结构（不落库，返回给前端回填表单）。
     */
    @PostMapping("/cases/import-jmx")
    public Result<PerfCaseEntity> importJmx(@RequestBody Map<String, String> body) {
        String jmx = body.get("jmx");
        if (jmx == null || jmx.isBlank()) {
            throw BizException.badRequest("JMX 内容不能为空");
        }
        return Result.ok(perfService.importJmx(jmx));
    }

    /**
     * 批量导出多个用例，合并为一个标准 JMeter .jmx 文件（下载）。
     */
    @PostMapping("/cases/export-batch")
    public ResponseEntity<byte[]> exportBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw BizException.badRequest("请选择要导出的用例");
        }
        String jmx = perfService.exportBatch(ids);
        byte[] bytes = jmx.getBytes(StandardCharsets.UTF_8);
        String filename = "perf-batch-" + ids.size() + "-cases.jmx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    // ---------------- 执行与报告 ----------------

    /**
     * 触发压测：先创建 pending 状态报告并落库，再按配置投递 MQ 异步执行。
     *
     * @param id 性能用例 ID（路径参数，必填）
     * @return 新创建的压测报告 VO（status=pending）
     * @audit 写审计日志：action=execute, entityType=perf_case
     */
    @AuditLog(action = "execute", entityType = "perf_case")
    @PostMapping("/cases/{id}/run")
    public Result<PerfReportVO> run(@PathVariable String id) {
        return Result.ok(perfService.run(id));
    }

    /**
     * 查询项目的压测报告列表（按开始时间倒序）。
     *
     * @param projectId 项目 ID（必填）
     * @param limit     返回条数上限
     * @return 压测报告列表
     */
    @GetMapping("/reports")
    public Result<List<PerfReportVO>> listReports(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "${apiweb.engine.list-max-limit:100}") int limit) {
        return Result.ok(perfService.listReports(projectId, limit));
    }

    /**
     * 获取压测报告详情。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 报告 VO
     */
    @GetMapping("/reports/{id}")
    public Result<PerfReportVO> getReport(@PathVariable String id) {
        return Result.ok(perfService.getReport(id));
    }

    /**
     * 删除压测报告（物理删除）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 无业务数据
     */
    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@PathVariable String id) {
        perfService.deleteReport(id);
        return Result.ok();
    }
}
