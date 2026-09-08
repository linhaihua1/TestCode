package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.PerfReportEntity;
import com.apiweb.mapper.PerfCaseMapper;
import com.apiweb.mapper.PerfReportMapper;
import com.apiweb.mq.TaskProducer;
import com.apiweb.service.OssService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

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

    // ---------------- 用例 ----------------

    @GetMapping("/cases")
    public Result<List<PerfCaseEntity>> listCases(@RequestParam String projectId) {
        return Result.ok(perfCaseMapper.selectList(
                new LambdaQueryWrapper<PerfCaseEntity>()
                        .eq(PerfCaseEntity::getProjectId, projectId)
                        .isNull(PerfCaseEntity::getDeletedAt)
                        .orderByDesc(PerfCaseEntity::getUpdatedAt)));
    }

    @GetMapping("/cases/{id}")
    public Result<PerfCaseEntity> getCase(@PathVariable String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("性能用例不存在");
        }
        return Result.ok(c);
    }

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

    // ---------------- 执行与报告 ----------------

    /**
     * 触发压测：创建报告（pending）并投递 RabbitMQ 异步执行。
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
        report.setStatus("pending");
        report.setStartedAt(Instant.now());
        report.setSummary("{}");
        report.setSeries("[]");
        report.setLabels("[]");
        report.setErrors("[]");
        perfReportMapper.insert(report);
        taskProducer.sendPerfTask(report.getId(), c.getProjectId(), id);
        return Result.ok(report);
    }

    @GetMapping("/reports")
    public Result<List<PerfReportEntity>> listReports(@RequestParam String projectId) {
        List<PerfReportEntity> reports = perfReportMapper.selectList(
                new LambdaQueryWrapper<PerfReportEntity>()
                        .eq(PerfReportEntity::getProjectId, projectId)
                        .orderByDesc(PerfReportEntity::getStartedAt)
                        .last("LIMIT 100"));
        for (PerfReportEntity r : reports) {
            if (r.getSeries() != null && r.getSeries().startsWith("minio://")) {
                r.setSeries(ossService.get(r.getSeries()));
            }
        }
        return Result.ok(reports);
    }

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

    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@PathVariable String id) {
        perfReportMapper.deleteById(id);
        return Result.ok();
    }
}
