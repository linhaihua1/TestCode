package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.TaskType;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.PerfReportEntity;
import com.apiweb.mapper.PerfCaseMapper;
import com.apiweb.mapper.PerfReportMapper;
import com.apiweb.mq.TaskProducer;
import com.apiweb.service.JmxImportExportService;
import com.apiweb.service.OssService;
import com.apiweb.service.PerfExecutionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.apiweb.engine.RunStatus;

/**
 * 性能测试（JMeter）：用例管理 + 异步执行 + 报告。
 */
@RestController
@RequestMapping("/api/v1/perf")
@RequiredArgsConstructor
public class PerfController {

    private final PerfCaseMapper perfCaseMapper;
    private final PerfReportMapper perfReportMapper;
    private final TaskProducer taskProducer;
    private final OssService ossService;
    private final JmxImportExportService jmxImportExportService;
    private final PerfExecutionService perfExecutionService;

    /**
     * 是否启用 RabbitMQ 消费端。localdev 下为 false，此时 run 接口改为 @Async 异步执行。
     */
    @Value("${apiweb.mq.enabled:true}")
    private boolean mqListenerEnabled;

    // ---------------- 用例 ----------------

    /**
     * 查询项目的性能用例列表（仅未删除，按更新时间倒序）。
     *
     * @param projectId 项目 ID（必填）
     * @return 性能用例列表
     */
    @GetMapping("/cases")
    public Result<List<PerfCaseEntity>> listCases(@RequestParam String projectId) {
        return Result.ok(perfCaseMapper.selectList(
                new LambdaQueryWrapper<PerfCaseEntity>()
                        .eq(PerfCaseEntity::getProjectId, projectId)
                        .isNull(PerfCaseEntity::getDeletedAt) // 过滤已软删除用例
                        .orderByDesc(PerfCaseEntity::getUpdatedAt)));
    }

    /**
     * 获取性能用例详情。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 性能用例实体（含 variables/steps/profile 等全量字段）
     * @throws BizException 用例不存在时抛 notFound
     */
    @GetMapping("/cases/{id}")
    public Result<PerfCaseEntity> getCase(@PathVariable String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("性能用例不存在");
        }
        return Result.ok(c);
    }

    /**
     * 新建性能用例：补齐 variables/steps/profile 默认空结构后落库。
     *
     * @param c 性能用例实体（请求体），name 必填
     * @return 已落库的用例实体
     * @throws BizException 用例名为空时抛 badRequest
     * @audit 写审计日志：action=create, entityType=perf_case
     */
    @AuditLog(action = "create", entityType = "perf_case")
    @PostMapping("/cases")
    public Result<PerfCaseEntity> createCase(@RequestBody PerfCaseEntity c) {
        if (c.getName() == null || c.getName().isBlank()) {
            throw BizException.badRequest("用例名称必填");
        }
        if (c.getVariables() == null) {
            c.setVariables("[]");
        }
        if (c.getSteps() == null) {
            c.setSteps("[]");
        }
        if (c.getProfile() == null) {
            c.setProfile("{}");
        }
        perfCaseMapper.insert(c);
        return Result.ok(c);
    }

    /**
     * 更新性能用例（覆盖请求体可写字段）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @param c  性能用例实体（请求体）
     * @return 无业务数据
     * @throws BizException 用例不存在时抛 notFound
     * @audit 写审计日志：action=update, entityType=perf_case
     */
    @AuditLog(action = "update", entityType = "perf_case")
    @PutMapping("/cases/{id}")
    public Result<Void> updateCase(@PathVariable String id, @RequestBody PerfCaseEntity c) {
        if (perfCaseMapper.selectById(id) == null) {
            throw BizException.notFound("性能用例不存在");
        }
        c.setId(id);
        perfCaseMapper.updateById(c);
        return Result.ok();
    }

    /**
     * 软删除性能用例（打 deletedAt，后续可在回收站恢复）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 无业务数据（不存在时静默成功）
     * @audit 写审计日志：action=delete, entityType=perf_case
     */
    @AuditLog(action = "delete", entityType = "perf_case")
    @DeleteMapping("/cases/{id}")
    public Result<Void> deleteCase(@PathVariable String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c != null) {
            c.setDeletedAt(Instant.now());
            perfCaseMapper.updateById(c);
        }
        return Result.ok();
    }

    // ---------------- JMX 导入 / 导出 ----------------

    /**
     * 导出用例为标准 JMeter .jmx 文件（下载）。
     */
    @GetMapping("/cases/{id}/export-jmx")
    public ResponseEntity<byte[]> exportJmx(@PathVariable String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("性能用例不存在");
        }
        String jmx = jmxImportExportService.exportJmx(c);
        byte[] bytes = jmx.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = (c.getName() == null ? "perf-case" : c.getName()) + ".jmx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename,
                                java.nio.charset.StandardCharsets.UTF_8))
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
        PerfCaseEntity parsed = jmxImportExportService.importJmx(jmx);
        return Result.ok(parsed);
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
        List<PerfCaseEntity> cases = perfCaseMapper.selectList(
                new LambdaQueryWrapper<PerfCaseEntity>().in(PerfCaseEntity::getId, ids));
        if (cases.isEmpty()) {
            throw BizException.notFound("未找到要导出的用例");
        }
        String jmx = jmxImportExportService.exportJmxMulti(cases);
        byte[] bytes = jmx.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "perf-batch-" + cases.size() + "-cases.jmx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    // ---------------- 执行与报告 ----------------

    /**
     * 触发压测：先创建 pending 状态报告并落库，再按配置投递 MQ 异步执行（localdev 无消费端时改 @Async 当前 JVM 内执行）。
     *
     * @param id 性能用例 ID（路径参数，必填）
     * @return 新创建的压测报告实体（status=pending）
     * @throws BizException 用例不存在时抛 notFound
     * @audit 写审计日志：action=execute, entityType=perf_case
     */
    @AuditLog(action = "execute", entityType = "perf_case")
    @PostMapping("/cases/{id}/run")
    public Result<PerfReportEntity> run(@PathVariable String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("性能用例不存在");
        }
        PerfReportEntity report = new PerfReportEntity();
        report.setProjectId(c.getProjectId());
        report.setCaseId(id);
        report.setName(c.getName());
        report.setStatus(RunStatus.PENDING.getCode());
        report.setStartedAt(Instant.now());
        report.setSummary("{}");
        report.setSeries("[]");
        report.setLabels("[]");
        report.setErrors("[]");
        perfReportMapper.insert(report);
        if (mqListenerEnabled) {
            taskProducer.sendPerfTask(report.getId(), c.getProjectId(), id);
        } else {
            // localdev：消费端关闭，改用 @Async 异步执行（嵌入式 JMeter 在当前 JVM 内跑）
            perfExecutionService.executeAsync(new EngineDtos.TaskMessage(
                    TaskType.PERF.getCode(), report.getId(), c.getProjectId(), id, null, null, null));
        }
        return Result.ok(report);
    }

    /**
     * 查询项目的压测报告列表（按开始时间倒序）。
     * <p>series 字段若为对象存储引用（minio:// 前缀）则实时拉取回填；limit 控制返回条数上限，避免一次回传过多拖垮接口。
     *
     * @param projectId 项目 ID（必填）
     * @param limit     返回条数上限（默认取配置项 apiweb.engine.list-max-limit，最小 1）
     * @return 压测报告列表（series 已解析为实际内容）
     */
    @GetMapping("/reports")
    public Result<List<PerfReportEntity>> listReports(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "${apiweb.engine.list-max-limit:100}") int limit) {
        List<PerfReportEntity> reports = perfReportMapper.selectList(
                new LambdaQueryWrapper<PerfReportEntity>()
                        .eq(PerfReportEntity::getProjectId, projectId)
                        .orderByDesc(PerfReportEntity::getStartedAt)
                        // Math.max(1, limit)：防止 limit<=0 导致 LIMIT 0 或非法 SQL，至少返回 1 条
                        .last("LIMIT " + Math.max(1, limit)));
        for (PerfReportEntity r : reports) {
            if (r.getSeries() != null && r.getSeries().startsWith("minio://")) {
                r.setSeries(ossService.get(r.getSeries()));
            }
        }
        return Result.ok(reports);
    }

    /**
     * 获取压测报告详情（series 若为 minio 引用则实时拉取回填）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 报告实体（含聚合指标 summary、时序曲线 series、标签分布 labels、错误明细 errors）
     * @throws BizException 报告不存在时抛 notFound
     */
    @GetMapping("/reports/{id}")
    public Result<PerfReportEntity> getReport(@PathVariable String id) {
        PerfReportEntity r = perfReportMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("报告不存在");
        }
        if (r.getSeries() != null && r.getSeries().startsWith("minio://")) {
            r.setSeries(ossService.get(r.getSeries()));
        }
        return Result.ok(r);
    }

    /**
     * 删除压测报告（物理删除）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 无业务数据
     */
    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@PathVariable String id) {
        perfReportMapper.deleteById(id);
        return Result.ok();
    }
}
