package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.common.PageResult;
import com.apiweb.converter.ReportConverter;
import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.mapper.ReportDetailMapper;
import com.apiweb.mapper.ReportMapper;
import com.apiweb.vo.report.ReportDetailVO;
import com.apiweb.vo.report.ReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 测试报告查询业务服务：列表 / 明细 / 删除。
 *
 * <p>导出与趋势统计分别由 {@code ReportExportService}、{@code TrendService} 负责，
 * 本服务聚焦报告与明细的 CRUD，统一分页返回 {@link PageResult}。</p>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final ReportDetailMapper reportDetailMapper;
    private final ReportConverter reportConverter;

    /**
     * 分页查询测试报告列表（按开始时间倒序）。
     */
    public PageResult<ReportVO> page(String projectId, String scenarioId, String taskId,
                                     long page, long size) {
        Page<ReportEntity> result = reportMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<ReportEntity>()
                        .eq(projectId != null && !projectId.isBlank(),
                                ReportEntity::getProjectId, projectId)
                        .eq(scenarioId != null && !scenarioId.isBlank(),
                                ReportEntity::getScenarioId, scenarioId)
                        .eq(taskId != null && !taskId.isBlank(),
                                ReportEntity::getTaskId, taskId)
                        .orderByDesc(ReportEntity::getStartedAt));
        List<ReportVO> records = reportConverter.toVOList(result.getRecords());
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取报告详情。
     */
    public ReportVO get(String id) {
        ReportEntity r = reportMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("报告不存在");
        }
        return reportConverter.toVO(r);
    }

    /**
     * 获取某报告的用例级执行明细列表。
     */
    public List<ReportDetailVO> details(String id) {
        if (reportMapper.selectById(id) == null) {
            throw BizException.notFound("报告不存在");
        }
        return reportConverter.toDetailVOList(reportDetailMapper.selectList(
                new LambdaQueryWrapper<ReportDetailEntity>()
                        .eq(ReportDetailEntity::getReportId, id)));
    }

    /**
     * 删除报告：先删明细再删主表。
     */
    @Transactional
    public void delete(String id) {
        reportDetailMapper.delete(new LambdaQueryWrapper<ReportDetailEntity>()
                .eq(ReportDetailEntity::getReportId, id));
        reportMapper.deleteById(id);
    }
}
