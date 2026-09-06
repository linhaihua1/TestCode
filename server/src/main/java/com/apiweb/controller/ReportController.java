package com.apiweb.controller;

import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.mapper.ReportDetailMapper;
import com.apiweb.mapper.ReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 接口测试报告。
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportMapper reportMapper;
    private final ReportDetailMapper reportDetailMapper;

    @GetMapping
    public Result<List<ReportEntity>> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String scenarioId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<ReportEntity> result = reportMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<ReportEntity>()
                        .eq(projectId != null && !projectId.isBlank(),
                                ReportEntity::getProjectId, projectId)
                        .eq(scenarioId != null && !scenarioId.isBlank(),
                                ReportEntity::getScenarioId, scenarioId)
                        .orderByDesc(ReportEntity::getStartedAt));
        return Result.ok(result.getRecords());
    }

    @GetMapping("/{id}")
    public Result<List<ReportDetailEntity>> detail(@PathVariable String id) {
        if (reportMapper.selectById(id) == null) {
            throw BizException.notFound("报告不存在");
        }
        return Result.ok(reportDetailMapper.selectList(
                new LambdaQueryWrapper<ReportDetailEntity>()
                        .eq(ReportDetailEntity::getReportId, id)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        reportDetailMapper.delete(new LambdaQueryWrapper<ReportDetailEntity>()
                .eq(ReportDetailEntity::getReportId, id));
        reportMapper.deleteById(id);
        return Result.ok();
    }
}
