package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.entity.ScenarioEntity;
import com.apiweb.entity.ScenarioStepEntity;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.VariablesResolver;
import com.apiweb.mapper.ReportDetailMapper;
import com.apiweb.mapper.ReportMapper;
import com.apiweb.mapper.ScenarioMapper;
import com.apiweb.mapper.ScenarioStepMapper;
import com.apiweb.service.ExecutionSupportService;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 接口场景管理 + 场景执行。
 */
@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioMapper scenarioMapper;
    private final ScenarioStepMapper scenarioStepMapper;
    private final ReportMapper reportMapper;
    private final ReportDetailMapper reportDetailMapper;
    private final ExecutionSupportService executionSupport;

    @GetMapping
    public Result<List<ScenarioEntity>> list(@RequestParam String projectId) {
        return Result.ok(scenarioMapper.selectList(
                new LambdaQueryWrapper<ScenarioEntity>()
                        .eq(ScenarioEntity::getProjectId, projectId)
                        .orderByDesc(ScenarioEntity::getUpdatedAt)));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable String id) {
        ScenarioEntity scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        List<ScenarioStepEntity> steps = scenarioStepMapper.selectList(
                new LambdaQueryWrapper<ScenarioStepEntity>()
                        .eq(ScenarioStepEntity::getScenarioId, id)
                        .orderByAsc(ScenarioStepEntity::getSortOrder));
        return Result.ok(Map.of("scenario", scenario, "steps", steps));
    }

    @AuditLog(action = "create", entityType = "scenario")
    @Transactional
    @PostMapping
    public Result<ScenarioEntity> create(@RequestBody Map<String, Object> body) {
        ScenarioEntity scenario = new ScenarioEntity();
        scenario.setProjectId(String.valueOf(body.get("projectId")));
        scenario.setName(String.valueOf(body.getOrDefault("name", "未命名场景")));
        scenario.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
        scenarioMapper.insert(scenario);
        saveSteps(scenario.getId(), body.get("steps"));
        return Result.ok(scenario);
    }

    @AuditLog(action = "update", entityType = "scenario")
    @Transactional
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        ScenarioEntity scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        if (body.containsKey("name")) {
            scenario.setName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("description")) {
            scenario.setDescription(String.valueOf(body.get("description")));
        }
        scenarioMapper.updateById(scenario);
        if (body.containsKey("steps")) {
            scenarioStepMapper.delete(new LambdaQueryWrapper<ScenarioStepEntity>()
                    .eq(ScenarioStepEntity::getScenarioId, id));
            saveSteps(id, body.get("steps"));
        }
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "scenario")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        scenarioStepMapper.delete(new LambdaQueryWrapper<ScenarioStepEntity>()
                .eq(ScenarioStepEntity::getScenarioId, id));
        scenarioMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 执行场景：按步骤顺序执行并生成报告。
     */
    @AuditLog(action = "execute", entityType = "scenario")
    @PostMapping("/{id}/execute")
    public Result<ReportEntity> execute(@PathVariable String id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        ScenarioEntity scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        List<ScenarioStepEntity> steps = scenarioStepMapper.selectList(
                new LambdaQueryWrapper<ScenarioStepEntity>()
                        .eq(ScenarioStepEntity::getScenarioId, id)
                        .orderByAsc(ScenarioStepEntity::getSortOrder));

        String environmentId = body == null || body.get("environmentId") == null ? null
                : String.valueOf(body.get("environmentId"));
        Map<String, String> vars = new java.util.LinkedHashMap<>();
        vars.putAll(executionSupport.buildEnvironmentVariables(environmentId));
        vars.putAll(executionSupport.buildGlobalVariables(scenario.getProjectId()));
        VariablesResolver resolver = new VariablesResolver(vars);

        ReportEntity report = new ReportEntity();
        report.setProjectId(scenario.getProjectId());
        report.setScenarioId(id);
        report.setName(scenario.getName());
        report.setStartedAt(Instant.now());
        report.setTriggerType("manual");
        // 先插入 report 拿到主键，否则明细的 reportId 会是 null
        reportMapper.insert(report);

        long start = System.currentTimeMillis();
        boolean allPass = true;
        int passedSteps = 0;
        int failedSteps = 0;
        int errorSteps = 0;
        int totalAssertions = 0;
        int passedAssertions = 0;
        int failedAssertions = 0;

        for (ScenarioStepEntity step : steps) {
            EngineDtos.ExecutionResult result = executionSupport.caseRunner()
                    .run(JsonUtils.toJson(Map.of(
                            "type", "http", "name", step.getName() == null ? "步骤" : step.getName(),
                            "url", "", "assertions", JsonUtils.toList(step.getAssertions()),
                            "extracts", JsonUtils.toList(step.getExtracts()))), resolver);
            ReportDetailEntity detail = new ReportDetailEntity();
            detail.setReportId(report.getId());
            detail.setStepName(step.getName() == null ? "步骤" : step.getName());
            EngineDtos.StepResult last = result.getSteps().isEmpty() ? null
                    : result.getSteps().get(result.getSteps().size() - 1);
            String status = last == null ? "error" : last.getStatus();
            detail.setStatus(status);
            detail.setError(last == null ? "无执行结果" : last.getError());
            detail.setAssertions(last == null ? "[]" : JsonUtils.toJson(last.getAssertions()));
            detail.setExtracts(last == null ? "[]" : JsonUtils.toJson(last.getExtracts()));
            reportDetailMapper.insert(detail);

            // 汇总统计
            if ("success".equals(status)) {
                passedSteps++;
            } else if ("error".equals(status)) {
                errorSteps++;
            } else {
                failedSteps++;
                allPass = false;
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
        report.setStatus(allPass ? "success" : (errorSteps > 0 ? "error" : "failed"));
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
        return Result.ok(report);
    }

    @SuppressWarnings("unchecked")
    private void saveSteps(String scenarioId, Object stepsObj) {
        if (!(stepsObj instanceof List<?> steps)) {
            return;
        }
        int order = 0;
        for (Object item : steps) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            ScenarioStepEntity step = new ScenarioStepEntity();
            step.setScenarioId(scenarioId);
            step.setSortOrder(order++);
            step.setApiCaseId(m.get("apiCaseId") == null ? null : String.valueOf(m.get("apiCaseId")));
            step.setName(m.get("name") == null ? null : String.valueOf(m.get("name")));
            step.setAssertions(m.get("assertions") == null ? "[]" : JsonUtils.toJson(m.get("assertions")));
            step.setExtracts(m.get("extracts") == null ? "[]" : JsonUtils.toJson(m.get("extracts")));
            scenarioStepMapper.insert(step);
        }
    }
}
