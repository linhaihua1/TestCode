package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.ScenarioConverter;
import com.apiweb.dto.scenario.ScenarioReq;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.ReportStatus;
import com.apiweb.engine.RunStatus;
import com.apiweb.engine.TriggerType;
import com.apiweb.engine.VariablesResolver;
import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.entity.ScenarioEntity;
import com.apiweb.entity.ScenarioStepEntity;
import com.apiweb.mapper.ReportDetailMapper;
import com.apiweb.mapper.ReportMapper;
import com.apiweb.mapper.ScenarioMapper;
import com.apiweb.mapper.ScenarioStepMapper;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.scenario.ScenarioVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口场景业务服务：场景管理 + 场景执行。
 */
@Service
@RequiredArgsConstructor
public class ScenarioService {

    private final ScenarioMapper scenarioMapper;
    private final ScenarioStepMapper scenarioStepMapper;
    private final ReportMapper reportMapper;
    private final ReportDetailMapper reportDetailMapper;
    private final ScenarioConverter scenarioConverter;
    private final ExecutionSupportService executionSupport;

    /**
     * 查询项目的场景列表（按更新时间倒序，不含步骤）。
     */
    public List<ScenarioVO> list(String projectId) {
        return scenarioConverter.toVOList(scenarioMapper.selectList(
                new LambdaQueryWrapper<ScenarioEntity>()
                        .eq(ScenarioEntity::getProjectId, projectId)
                        .orderByDesc(ScenarioEntity::getUpdatedAt)));
    }

    /**
     * 获取场景详情（含步骤列表）。
     */
    public ScenarioVO get(String id) {
        ScenarioEntity scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        ScenarioVO vo = scenarioConverter.toVO(scenario);
        vo.setSteps(scenarioConverter.toStepVOList(scenarioStepMapper.selectList(
                new LambdaQueryWrapper<ScenarioStepEntity>()
                        .eq(ScenarioStepEntity::getScenarioId, id)
                        .orderByAsc(ScenarioStepEntity::getSortOrder))));
        return vo;
    }

    /**
     * 新建场景（含步骤）。
     */
    @Transactional
    public ScenarioVO create(ScenarioReq req) {
        ScenarioEntity scenario = new ScenarioEntity();
        scenario.setProjectId(req.getProjectId());
        scenario.setName(req.getName() == null || req.getName().isBlank() ? "未命名场景" : req.getName());
        scenario.setDescription(req.getDescription());
        scenarioMapper.insert(scenario);
        saveSteps(scenario.getId(), req.getSteps());
        return scenarioConverter.toVO(scenario);
    }

    /**
     * 更新场景（含可选步骤整体替换）。
     */
    @Transactional
    public void update(String id, ScenarioReq req) {
        ScenarioEntity scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        if (req.getName() != null) {
            scenario.setName(req.getName());
        }
        if (req.getDescription() != null) {
            scenario.setDescription(req.getDescription());
        }
        scenarioMapper.updateById(scenario);
        if (req.getSteps() != null) {
            scenarioStepMapper.delete(new LambdaQueryWrapper<ScenarioStepEntity>()
                    .eq(ScenarioStepEntity::getScenarioId, id));
            saveSteps(id, req.getSteps());
        }
    }

    /**
     * 删除场景：先删步骤再删主表。
     */
    @Transactional
    public void delete(String id) {
        scenarioStepMapper.delete(new LambdaQueryWrapper<ScenarioStepEntity>()
                .eq(ScenarioStepEntity::getScenarioId, id));
        scenarioMapper.deleteById(id);
    }

    /**
     * 执行场景：按步骤顺序执行并汇总生成报告（同步执行）。
     */
    public ReportEntity execute(String id, String environmentId) {
        ScenarioEntity scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        List<ScenarioStepEntity> steps = scenarioStepMapper.selectList(
                new LambdaQueryWrapper<ScenarioStepEntity>()
                        .eq(ScenarioStepEntity::getScenarioId, id)
                        .orderByAsc(ScenarioStepEntity::getSortOrder));

        Map<String, String> vars = new LinkedHashMap<>();
        vars.putAll(executionSupport.buildEnvironmentVariables(environmentId));
        vars.putAll(executionSupport.buildGlobalVariables(scenario.getProjectId()));
        VariablesResolver resolver = new VariablesResolver(vars);

        ReportEntity report = new ReportEntity();
        report.setProjectId(scenario.getProjectId());
        report.setScenarioId(id);
        report.setName(scenario.getName());
        report.setStartedAt(Instant.now());
        report.setTriggerType(TriggerType.MANUAL.getCode());
        report.setStatus(ReportStatus.RUNNING.getCode());
        report.setTotalCases(0);
        report.setPassedCases(0);
        report.setFailedCases(0);
        report.setErrorCases(0);
        report.setSkippedCases(0);
        reportMapper.insert(report);

        long start = System.currentTimeMillis();
        int passedSteps = 0;
        int failedSteps = 0;
        int errorSteps = 0;
        int totalAssertions = 0;
        int passedAssertions = 0;
        int failedAssertions = 0;

        for (ScenarioStepEntity step : steps) {
            EngineDtos.ExecutionResult result;
            if (step.getApiCaseId() != null && !step.getApiCaseId().isBlank()) {
                result = executionSupport.caseRunner().runCaseSteps(step.getApiCaseId(), resolver);
            } else {
                result = executionSupport.caseRunner()
                        .run(JsonUtils.toJson(Map.of(
                                "type", "http", "name", step.getName() == null ? "步骤" : step.getName(),
                                "url", "", "assertions", JsonUtils.toList(step.getAssertions()),
                                "extracts", JsonUtils.toList(step.getExtracts()))), resolver);
            }
            ReportDetailEntity detail = new ReportDetailEntity();
            detail.setReportId(report.getId());
            detail.setStepName(step.getName() == null ? "步骤" : step.getName());
            EngineDtos.StepResult last = result.getSteps().isEmpty() ? null
                    : result.getSteps().get(result.getSteps().size() - 1);
            String status = last == null ? RunStatus.ERROR.getCode() : last.getStatus();
            detail.setStatus(status);
            detail.setError(last == null ? "无执行结果" : last.getError());
            detail.setAssertions(last == null ? "[]" : JsonUtils.toJson(last.getAssertions()));
            detail.setExtracts(last == null ? "[]" : JsonUtils.toJson(last.getExtracts()));
            reportDetailMapper.insert(detail);

            if (RunStatus.SUCCESS.getCode().equals(status)) {
                passedSteps++;
            } else if (RunStatus.ERROR.getCode().equals(status)) {
                errorSteps++;
            } else {
                failedSteps++;
            }
            if (last != null && last.getAssertions() != null) {
                for (EngineDtos.AssertionResult a : last.getAssertions()) {
                    totalAssertions++;
                    if (a.isPassed()) {
                        passedAssertions++;
                    } else {
                        failedAssertions++;
                    }
                }
            }
        }
        // 统一由计数推导状态，避免额外维护 allPass 标志导致"有异常仍判通过"
        report.setStatus(ReportStatus.resolve(errorSteps, failedSteps, steps.size()).getCode());
        report.setTotalCases(steps.size());
        report.setPassedCases(passedSteps);
        report.setFailedCases(failedSteps);
        report.setErrorCases(errorSteps);
        report.setSkippedCases(0);
        report.setTotalAssertions(totalAssertions);
        report.setPassedAssertions(passedAssertions);
        report.setFailedAssertions(failedAssertions);
        report.setDuration((int) (System.currentTimeMillis() - start));
        report.setFinishedAt(Instant.now());
        reportMapper.updateById(report);
        return report;
    }

    private void saveSteps(String scenarioId, List<ScenarioReq.StepReq> steps) {
        if (steps == null) {
            return;
        }
        int order = 0;
        for (ScenarioReq.StepReq item : steps) {
            ScenarioStepEntity step = new ScenarioStepEntity();
            step.setScenarioId(scenarioId);
            step.setSortOrder(order++);
            step.setApiCaseId(item.getApiCaseId());
            step.setName(item.getName());
            step.setAssertions(item.getAssertions() == null ? "[]" : JsonUtils.toJson(item.getAssertions()));
            step.setExtracts(item.getExtracts() == null ? "[]" : JsonUtils.toJson(item.getExtracts()));
            scenarioStepMapper.insert(step);
        }
    }
}
