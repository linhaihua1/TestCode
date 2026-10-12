package com.apiweb.service;

import com.apiweb.common.AfterCommit;
import com.apiweb.common.BizException;
import com.apiweb.converter.UiConverter;
import com.apiweb.dto.ui.UiScenarioReq;
import com.apiweb.dto.ui.UiTestCaseReq;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.RunStatus;
import com.apiweb.engine.TaskType;
import com.apiweb.entity.UiReportEntity;
import com.apiweb.entity.UiScenarioEntity;
import com.apiweb.entity.UiScenarioStepEntity;
import com.apiweb.entity.UiTestCaseEntity;
import com.apiweb.mapper.UiReportMapper;
import com.apiweb.mapper.UiScenarioMapper;
import com.apiweb.mapper.UiScenarioStepMapper;
import com.apiweb.mapper.UiTestCaseMapper;
import com.apiweb.mq.TaskProducer;
import com.apiweb.vo.ui.UiReportVO;
import com.apiweb.vo.ui.UiScenarioStepVO;
import com.apiweb.vo.ui.UiScenarioVO;
import com.apiweb.vo.ui.UiTestCaseVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UI 自动化业务服务。
 *
 * <p>承载原 {@code UiController} 中的业务逻辑：UI 用例 CRUD、执行场景 CRUD、
 * 执行与报告。Controller 仅保留参数校验与响应包装。</p>
 */
@Service
@RequiredArgsConstructor
public class UiService {

    /** UI 用例数据访问层 */
    private final UiTestCaseMapper uiTestCaseMapper;
    /** UI 场景数据访问层 */
    private final UiScenarioMapper uiScenarioMapper;
    /** UI 场景步骤数据访问层 */
    private final UiScenarioStepMapper uiScenarioStepMapper;
    /** UI 报告数据访问层 */
    private final UiReportMapper uiReportMapper;
    /** 任务生产者（投递 MQ 异步执行） */
    private final TaskProducer taskProducer;
    /** 对象存储服务（大报告明细转存） */
    private final OssService ossService;
    /** UI 执行服务（Selenium 驱动真实浏览器） */
    private final UiExecutionService uiExecutionService;
    /** UI 相关实体 ↔ DTO/VO 转换器 */
    private final UiConverter converter;

    /** 是否启用 RabbitMQ 消费端。localdev 下为 false，run 接口改为 @Async 异步执行。 */
    @Value("${apiweb.mq.enabled:true}")
    private boolean mqListenerEnabled;

    // ---------------- UI 测试用例 ----------------

    /**
     * 查询项目的 UI 测试用例列表。
     */
    public List<UiTestCaseVO> listTests(String projectId) {
        return converter.toTestCaseVOList(uiTestCaseMapper.selectList(
                new LambdaQueryWrapper<UiTestCaseEntity>()
                        .eq(UiTestCaseEntity::getProjectId, projectId)
                        .orderByDesc(UiTestCaseEntity::getUpdatedAt)));
    }

    /**
     * 获取 UI 用例详情。
     */
    public UiTestCaseVO getTest(String id) {
        UiTestCaseEntity t = uiTestCaseMapper.selectById(id);
        if (t == null) {
            throw BizException.notFound("UI 用例不存在");
        }
        return converter.toTestCaseVO(t);
    }

    /**
     * 新建 UI 用例。
     */
    @Transactional
    public UiTestCaseVO createTest(UiTestCaseReq req) {
        UiTestCaseEntity t = converter.toEntity(req);
        if (t.getSetupSteps() == null) t.setSetupSteps("[]");
        if (t.getSteps() == null) t.setSteps("[]");
        if (t.getTeardownSteps() == null) t.setTeardownSteps("[]");
        uiTestCaseMapper.insert(t);
        return converter.toTestCaseVO(t);
    }

    /**
     * 更新 UI 用例。
     */
    @Transactional
    public void updateTest(String id, UiTestCaseReq req) {
        if (uiTestCaseMapper.selectById(id) == null) {
            throw BizException.notFound("UI 用例不存在");
        }
        UiTestCaseEntity t = converter.toEntity(req);
        t.setId(id);
        uiTestCaseMapper.updateById(t);
    }

    /**
     * 删除 UI 用例。
     */
    @Transactional
    public void deleteTest(String id) {
        uiTestCaseMapper.deleteById(id);
    }

    // ---------------- UI 执行场景 ----------------

    /**
     * 查询项目的 UI 执行场景列表。
     */
    public List<UiScenarioVO> listScenarios(String projectId) {
        return converter.toScenarioVOList(uiScenarioMapper.selectList(
                new LambdaQueryWrapper<UiScenarioEntity>()
                        .eq(UiScenarioEntity::getProjectId, projectId)
                        .orderByDesc(UiScenarioEntity::getUpdatedAt)));
    }

    /**
     * 获取 UI 场景详情（含步骤列表）。
     *
     * @return { scenario: 场景 VO, steps: 步骤 VO 列表 }
     */
    public Map<String, Object> getScenario(String id) {
        UiScenarioEntity scenario = uiScenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        List<UiScenarioStepVO> steps = converter.toScenarioStepVOList(uiScenarioStepMapper.selectList(
                new LambdaQueryWrapper<UiScenarioStepEntity>()
                        .eq(UiScenarioStepEntity::getScenarioId, id)
                        .orderByAsc(UiScenarioStepEntity::getSortOrder)));
        return Map.of("scenario", converter.toScenarioVO(scenario), "steps", steps);
    }

    /**
     * 新建 UI 场景。
     */
    @Transactional
    public UiScenarioVO createScenario(UiScenarioReq req) {
        UiScenarioEntity scenario = new UiScenarioEntity();
        scenario.setProjectId(req.getProjectId());
        scenario.setName(req.getName() == null ? "未命名场景" : req.getName());
        scenario.setDescription(req.getDescription());
        applyScenarioConfig(scenario, req);
        uiScenarioMapper.insert(scenario);
        saveScenarioSteps(scenario.getId(), req.getSteps());
        return converter.toScenarioVO(scenario);
    }

    /**
     * 更新 UI 场景。
     */
    @Transactional
    public void updateScenario(String id, UiScenarioReq req) {
        UiScenarioEntity scenario = uiScenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        if (req.getName() != null) {
            scenario.setName(req.getName());
        }
        scenario.setDescription(req.getDescription());
        applyScenarioConfig(scenario, req);
        uiScenarioMapper.updateById(scenario);
        if (req.getSteps() != null) {
            uiScenarioStepMapper.delete(new LambdaQueryWrapper<UiScenarioStepEntity>()
                    .eq(UiScenarioStepEntity::getScenarioId, id));
            saveScenarioSteps(id, req.getSteps());
        }
    }

    /**
     * 删除 UI 场景。
     */
    @Transactional
    public void deleteScenario(String id) {
        uiScenarioStepMapper.delete(new LambdaQueryWrapper<UiScenarioStepEntity>()
                .eq(UiScenarioStepEntity::getScenarioId, id));
        uiScenarioMapper.deleteById(id);
    }

    // ---------------- 执行与报告 ----------------

    /**
     * 触发 UI 用例执行。
     */
    @Transactional
    public UiReportVO runTest(String id) {
        UiTestCaseEntity t = uiTestCaseMapper.selectById(id);
        if (t == null) {
            throw BizException.notFound("UI 用例不存在");
        }
        UiReportEntity report = new UiReportEntity();
        report.setProjectId(t.getProjectId());
        report.setTestCaseId(id);
        report.setName(t.getName());
        report.setStatus(RunStatus.PENDING.getCode());
        report.setStartedAt(Instant.now());
        report.setDetails("[]");
        uiReportMapper.insert(report);
        // 必须等事务提交后再派发，否则异步线程读不到刚插入的 PENDING 报告
        if (mqListenerEnabled) {
            AfterCommit.run(() -> taskProducer.sendUiTask(report.getId(), t.getProjectId(), id));
        } else {
            AfterCommit.run(() -> uiExecutionService.executeAsync(new EngineDtos.TaskMessage(
                    TaskType.UI.getCode(), report.getId(), t.getProjectId(), id, null, null, null)));
        }
        return converter.toReportVO(report);
    }

    /**
     * 触发 UI 场景执行。
     */
    @Transactional
    public List<UiReportVO> runScenario(String id) {
        UiScenarioEntity scenario = uiScenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        List<UiScenarioStepEntity> steps = uiScenarioStepMapper.selectList(
                new LambdaQueryWrapper<UiScenarioStepEntity>()
                        .eq(UiScenarioStepEntity::getScenarioId, id)
                        .orderByAsc(UiScenarioStepEntity::getSortOrder));
        Map<String, Object> extra = new HashMap<>();
        if (scenario.getRetryCount() != null) {
            extra.put("retryCount", scenario.getRetryCount());
        }
        if (scenario.getTimeoutMs() != null) {
            extra.put("timeoutMs", scenario.getTimeoutMs());
        }
        if (scenario.getExecutorUrl() != null && !scenario.getExecutorUrl().isBlank()) {
            extra.put("executorUrl", scenario.getExecutorUrl());
        }
        if (scenario.getExecutorHost() != null && !scenario.getExecutorHost().isBlank()) {
            extra.put("executorHost", scenario.getExecutorHost());
        }
        if (scenario.getExecutorPort() != null) {
            extra.put("executorPort", scenario.getExecutorPort());
        }
        List<UiReportVO> reports = new ArrayList<>();
        for (UiScenarioStepEntity step : steps) {
            UiTestCaseEntity t = step.getUiTestCaseId() == null ? null
                    : uiTestCaseMapper.selectById(step.getUiTestCaseId());
            if (t == null) {
                continue;
            }
            UiReportEntity report = new UiReportEntity();
            report.setProjectId(scenario.getProjectId());
            report.setTestCaseId(t.getId());
            report.setName(scenario.getName() + " / " + t.getName());
            report.setStatus(RunStatus.PENDING.getCode());
            report.setStartedAt(Instant.now());
            report.setDetails("[]");
            uiReportMapper.insert(report);
            // 同上：事务提交后再派发，避免异步线程读不到尚未提交的报告
            if (mqListenerEnabled) {
                AfterCommit.run(() -> taskProducer.sendUiTask(report.getId(), scenario.getProjectId(),
                        t.getId(), scenario.getEnvironmentId(), extra));
            } else {
                AfterCommit.run(() -> uiExecutionService.executeAsync(new EngineDtos.TaskMessage(
                        TaskType.UI.getCode(), report.getId(), scenario.getProjectId(), t.getId(),
                        null, scenario.getEnvironmentId(), extra)));
            }
            reports.add(converter.toReportVO(report));
        }
        return reports;
    }

    /**
     * 查询项目的 UI 报告列表。
     */
    public List<UiReportVO> listReports(String projectId, int limit) {
        List<UiReportEntity> reports = uiReportMapper.selectList(
                new LambdaQueryWrapper<UiReportEntity>()
                        .eq(UiReportEntity::getProjectId, projectId)
                        .orderByDesc(UiReportEntity::getStartedAt)
                        .last("LIMIT " + Math.max(1, limit)));
        for (UiReportEntity r : reports) {
            if (r.getDetails() != null && r.getDetails().startsWith("minio://")) {
                r.setDetails(ossService.get(r.getDetails()));
            }
        }
        return converter.toReportVOList(reports);
    }

    /**
     * 获取 UI 报告详情。
     */
    public UiReportVO getReport(String id) {
        UiReportEntity r = uiReportMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("报告不存在");
        }
        if (r.getDetails() != null && r.getDetails().startsWith("minio://")) {
            r.setDetails(ossService.get(r.getDetails()));
        }
        return converter.toReportVO(r);
    }

    /**
     * 删除 UI 报告。
     */
    @Transactional
    public void deleteReport(String id) {
        uiReportMapper.deleteById(id);
    }

    // ---------------- 内部 ----------------

    private void applyScenarioConfig(UiScenarioEntity scenario, UiScenarioReq req) {
        if (req.getEnvironmentId() != null) {
            scenario.setEnvironmentId(req.getEnvironmentId());
        }
        // variables 已由 JsonStringDeserializer 统一转为 JSON 字符串，直接存储即可
        if (req.getVariables() != null) {
            scenario.setVariables(req.getVariables());
        }
        if (req.getExecutorUrl() != null) {
            scenario.setExecutorUrl(req.getExecutorUrl());
        }
        if (req.getExecutorHost() != null) {
            scenario.setExecutorHost(req.getExecutorHost());
        }
        if (req.getExecutorPort() != null) {
            scenario.setExecutorPort(req.getExecutorPort());
        }
        if (req.getRetryCount() != null) {
            scenario.setRetryCount(req.getRetryCount());
        }
        if (req.getTimeoutMs() != null) {
            scenario.setTimeoutMs(req.getTimeoutMs());
        }
    }

    private void saveScenarioSteps(String scenarioId, List<Map<String, Object>> steps) {
        if (steps == null) {
            return;
        }
        int order = 0;
        for (Map<String, Object> m : steps) {
            UiScenarioStepEntity step = new UiScenarioStepEntity();
            step.setScenarioId(scenarioId);
            step.setSortOrder(order++);
            step.setUiTestCaseId(m.get("uiTestCaseId") == null ? null
                    : String.valueOf(m.get("uiTestCaseId")));
            uiScenarioStepMapper.insert(step);
        }
    }
}
