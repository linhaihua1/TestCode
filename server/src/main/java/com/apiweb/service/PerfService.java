package com.apiweb.service;

import com.apiweb.common.AfterCommit;
import com.apiweb.common.BizException;
import com.apiweb.converter.PerfConverter;
import com.apiweb.dto.perf.PerfCaseReq;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.RunStatus;
import com.apiweb.engine.TaskType;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.PerfReportEntity;
import com.apiweb.mapper.PerfCaseMapper;
import com.apiweb.mapper.PerfReportMapper;
import com.apiweb.mq.TaskProducer;
import com.apiweb.vo.perf.PerfCaseVO;
import com.apiweb.vo.perf.PerfReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 性能测试业务服务：用例管理 + 异步执行 + 报告。
 */
@Service
@RequiredArgsConstructor
public class PerfService {

    private final PerfCaseMapper perfCaseMapper;
    private final PerfReportMapper perfReportMapper;
    private final PerfConverter perfConverter;
    private final TaskProducer taskProducer;
    private final OssService ossService;
    private final JmxImportExportService jmxImportExportService;
    private final PerfExecutionService perfExecutionService;

    /** 是否启用 RabbitMQ 消费端。localdev 下为 false，run 改 @Async 执行。 */
    @Value("${apiweb.mq.enabled:true}")
    private boolean mqListenerEnabled;

    // ---------------- 用例 ----------------

    /**
     * 查询项目的性能用例列表（仅未删除，按更新时间倒序）。
     */
    public List<PerfCaseVO> listCases(String projectId) {
        return perfConverter.toCaseVOList(perfCaseMapper.selectList(
                new LambdaQueryWrapper<PerfCaseEntity>()
                        .eq(PerfCaseEntity::getProjectId, projectId)
                        .isNull(PerfCaseEntity::getDeletedAt)
                        .orderByDesc(PerfCaseEntity::getUpdatedAt)));
    }

    /**
     * 获取性能用例详情。
     */
    public PerfCaseVO getCase(String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("性能用例不存在");
        }
        return perfConverter.toCaseVO(c);
    }

    /**
     * 获取性能用例实体（供导出等内部使用）。
     */
    public PerfCaseEntity getCaseEntity(String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("性能用例不存在");
        }
        return c;
    }

    /**
     * 新建性能用例：补齐默认空结构后落库。
     */
    @Transactional
    public PerfCaseVO createCase(PerfCaseReq req) {
        PerfCaseEntity c = perfConverter.toEntity(req);
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
        return perfConverter.toCaseVO(c);
    }

    /**
     * 更新性能用例。
     */
    @Transactional
    public void updateCase(String id, PerfCaseReq req) {
        if (perfCaseMapper.selectById(id) == null) {
            throw BizException.notFound("性能用例不存在");
        }
        PerfCaseEntity c = perfConverter.toEntity(req);
        c.setId(id);
        perfCaseMapper.updateById(c);
    }

    /**
     * 软删除性能用例。
     */
    @Transactional
    public void deleteCase(String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c != null) {
            c.setDeletedAt(Instant.now());
            perfCaseMapper.updateById(c);
        }
    }

    // ---------------- JMX 导入 / 导出 ----------------

    /**
     * 导出用例为标准 JMeter .jmx 字符串。
     */
    public String exportJmx(String id) {
        PerfCaseEntity c = perfCaseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("性能用例不存在");
        }
        return jmxImportExportService.exportJmx(c);
    }

    /**
     * 从 .jmx 字符串解析为性能用例结构（不落库）。
     *
     * <p>返回 VO（steps/variables/profile 已解析为结构化对象），供前端直接回填表单。</p>
     */
    public PerfCaseVO importJmx(String jmx) {
        return perfConverter.toCaseVO(jmxImportExportService.importJmx(jmx));
    }

    /**
     * 批量导出多个用例，合并为一个 .jmx 字符串。
     */
    public String exportBatch(List<String> ids) {
        List<PerfCaseEntity> cases = perfCaseMapper.selectList(
                new LambdaQueryWrapper<PerfCaseEntity>().in(PerfCaseEntity::getId, ids));
        if (cases.isEmpty()) {
            throw BizException.notFound("未找到要导出的用例");
        }
        return jmxImportExportService.exportJmxMulti(cases);
    }

    // ---------------- 执行与报告 ----------------

    /**
     * 触发压测：创建 pending 报告并投递 MQ 或 @Async 执行。
     */
    @Transactional
    public PerfReportVO run(String id) {
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
        // 必须等事务提交后再派发，否则异步线程读不到刚插入的 PENDING 报告
        if (mqListenerEnabled) {
            AfterCommit.run(() -> taskProducer.sendPerfTask(report.getId(), c.getProjectId(), id));
        } else {
            AfterCommit.run(() -> perfExecutionService.executeAsync(new EngineDtos.TaskMessage(
                    TaskType.PERF.getCode(), report.getId(), c.getProjectId(), id, null, null, null)));
        }
        return perfConverter.toReportVO(report);
    }

    /**
     * 查询项目的压测报告列表（series 若为 minio 引用则回填）。
     */
    public List<PerfReportVO> listReports(String projectId, int limit) {
        List<PerfReportEntity> reports = perfReportMapper.selectList(
                new LambdaQueryWrapper<PerfReportEntity>()
                        .eq(PerfReportEntity::getProjectId, projectId)
                        .orderByDesc(PerfReportEntity::getStartedAt)
                        .last("LIMIT " + Math.max(1, limit)));
        for (PerfReportEntity r : reports) {
            if (r.getSeries() != null && r.getSeries().startsWith("minio://")) {
                r.setSeries(ossService.get(r.getSeries()));
            }
        }
        return perfConverter.toReportVOList(reports);
    }

    /**
     * 获取压测报告详情（series 若为 minio 引用则回填）。
     */
    public PerfReportVO getReport(String id) {
        PerfReportEntity r = perfReportMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("报告不存在");
        }
        if (r.getSeries() != null && r.getSeries().startsWith("minio://")) {
            r.setSeries(ossService.get(r.getSeries()));
        }
        return perfConverter.toReportVO(r);
    }

    /**
     * 删除压测报告。
     */
    @Transactional
    public void deleteReport(String id) {
        perfReportMapper.deleteById(id);
    }
}
