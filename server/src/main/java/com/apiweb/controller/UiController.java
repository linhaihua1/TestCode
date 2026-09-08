package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.UiReportEntity;
import com.apiweb.entity.UiScenarioEntity;
import com.apiweb.entity.UiScenarioStepEntity;
import com.apiweb.entity.UiTestCaseEntity;
import com.apiweb.mapper.UiReportMapper;
import com.apiweb.mapper.UiScenarioMapper;
import com.apiweb.mapper.UiScenarioStepMapper;
import com.apiweb.mapper.UiTestCaseMapper;
import com.apiweb.mq.TaskProducer;
import com.apiweb.service.OssService;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * UI 自动化：测试用例 / 执行场景 / 执行与报告。
 */
@RestController
@RequestMapping("/api/v1/ui")
@RequiredArgsConstructor
public class UiController {

    private final UiTestCaseMapper uiTestCaseMapper;
    private final UiScenarioMapper uiScenarioMapper;
    private final UiScenarioStepMapper uiScenarioStepMapper;
    private final UiReportMapper uiReportMapper;
    private final TaskProducer taskProducer;
    private final OssService ossService;

    // ---------------- UI 测试用例 ----------------

    @GetMapping("/tests")
    public Result<List<UiTestCaseEntity>> listTests(@RequestParam String projectId) {
        return Result.ok(uiTestCaseMapper.selectList(
                new LambdaQueryWrapper<UiTestCaseEntity>()
                        .eq(UiTestCaseEntity::getProjectId, projectId)
                        .orderByDesc(UiTestCaseEntity::getUpdatedAt)));
    }

    @GetMapping("/tests/{id}")
    public Result<UiTestCaseEntity> getTest(@PathVariable String id) {
        UiTestCaseEntity t = uiTestCaseMapper.selectById(id);
        if (t == null) {
            throw BizException.notFound("UI 用例不存在");
        }
        return Result.ok(t);
    }

    @AuditLog(action = "create", entityType = "ui_test")
    @PostMapping("/tests")
    public Result<UiTestCaseEntity> createTest(@RequestBody UiTestCaseEntity t) {
        if (t.getName() == null || t.getName().isBlank()) {
            throw BizException.badRequest("用例名称必填");
        }
        if (t.getSetupSteps() == null) {
            t.setSetupSteps("[]");
        }
        if (t.getSteps() == null) {
            t.setSteps("[]");
        }
        if (t.getTeardownSteps() == null) {
            t.setTeardownSteps("[]");
        }
        uiTestCaseMapper.insert(t);
        return Result.ok(t);
    }

    @AuditLog(action = "update", entityType = "ui_test")
    @PutMapping("/tests/{id}")
    public Result<Void> updateTest(@PathVariable String id, @RequestBody UiTestCaseEntity t) {
        if (uiTestCaseMapper.selectById(id) == null) {
            throw BizException.notFound("UI 用例不存在");
        }
        t.setId(id);
        uiTestCaseMapper.updateById(t);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "ui_test")
    @DeleteMapping("/tests/{id}")
    public Result<Void> deleteTest(@PathVariable String id) {
        uiTestCaseMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- UI 执行场景 ----------------

    @GetMapping("/scenarios")
    public Result<List<UiScenarioEntity>> listScenarios(@RequestParam String projectId) {
        return Result.ok(uiScenarioMapper.selectList(
                new LambdaQueryWrapper<UiScenarioEntity>()
                        .eq(UiScenarioEntity::getProjectId, projectId)
                        .orderByDesc(UiScenarioEntity::getUpdatedAt)));
    }

    @GetMapping("/scenarios/{id}")
    public Result<Map<String, Object>> getScenario(@PathVariable String id) {
        UiScenarioEntity scenario = uiScenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        List<UiScenarioStepEntity> steps = uiScenarioStepMapper.selectList(
                new LambdaQueryWrapper<UiScenarioStepEntity>()
                        .eq(UiScenarioStepEntity::getScenarioId, id)
                        .orderByAsc(UiScenarioStepEntity::getSortOrder));
        return Result.ok(Map.of("scenario", scenario, "steps", steps));
    }

    @AuditLog(action = "create", entityType = "ui_scenario")
    @Transactional
    @PostMapping("/scenarios")
    public Result<UiScenarioEntity> createScenario(@RequestBody Map<String, Object> body) {
        UiScenarioEntity scenario = new UiScenarioEntity();
        scenario.setProjectId(String.valueOf(body.get("projectId")));
        scenario.setName(String.valueOf(body.getOrDefault("name", "未命名场景")));
        scenario.setDescription(body.get("description") == null ? null
                : String.valueOf(body.get("description")));
        uiScenarioMapper.insert(scenario);
        saveScenarioSteps(scenario.getId(), body.get("steps"));
        return Result.ok(scenario);
    }

    @AuditLog(action = "update", entityType = "ui_scenario")
    @Transactional
    @PutMapping("/scenarios/{id}")
    public Result<Void> updateScenario(@PathVariable String id, @RequestBody Map<String, Object> body) {
        UiScenarioEntity scenario = uiScenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        if (body.containsKey("name")) {
            scenario.setName(String.valueOf(body.get("name")));
        }
        uiScenarioMapper.updateById(scenario);
        if (body.containsKey("steps")) {
            uiScenarioStepMapper.delete(new LambdaQueryWrapper<UiScenarioStepEntity>()
                    .eq(UiScenarioStepEntity::getScenarioId, id));
            saveScenarioSteps(id, body.get("steps"));
        }
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "ui_scenario")
    @Transactional
    @DeleteMapping("/scenarios/{id}")
    public Result<Void> deleteScenario(@PathVariable String id) {
        uiScenarioStepMapper.delete(new LambdaQueryWrapper<UiScenarioStepEntity>()
                .eq(UiScenarioStepEntity::getScenarioId, id));
        uiScenarioMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- 执行与报告 ----------------

    /**
     * 触发 UI 用例执行：创建报告并投递 RabbitMQ。
     */
    @AuditLog(action = "execute", entityType = "ui_test")
    @PostMapping("/tests/{id}/run")
    public Result<UiReportEntity> runTest(@PathVariable String id) {
        UiTestCaseEntity t = uiTestCaseMapper.selectById(id);
        if (t == null) {
            throw BizException.notFound("UI 用例不存在");
        }
        UiReportEntity report = new UiReportEntity();
        report.setProjectId(t.getProjectId());
        report.setTestCaseId(id);
        report.setName(t.getName());
        report.setStatus("pending");
        report.setStartedAt(Instant.now());
        report.setDetails("[]");
        uiReportMapper.insert(report);
        taskProducer.sendUiTask(report.getId(), t.getProjectId(), id);
        return Result.ok(report);
    }

    /**
     * 触发 UI 场景执行：依次为每个步骤用例创建报告并投递。
     */
    @AuditLog(action = "execute", entityType = "ui_scenario")
    @PostMapping("/scenarios/{id}/run")
    public Result<List<UiReportEntity>> runScenario(@PathVariable String id) {
        UiScenarioEntity scenario = uiScenarioMapper.selectById(id);
        if (scenario == null) {
            throw BizException.notFound("场景不存在");
        }
        List<UiScenarioStepEntity> steps = uiScenarioStepMapper.selectList(
                new LambdaQueryWrapper<UiScenarioStepEntity>()
                        .eq(UiScenarioStepEntity::getScenarioId, id)
                        .orderByAsc(UiScenarioStepEntity::getSortOrder));
        List<UiReportEntity> reports = new java.util.ArrayList<>();
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
            report.setStatus("pending");
            report.setStartedAt(Instant.now());
            report.setDetails("[]");
            uiReportMapper.insert(report);
            taskProducer.sendUiTask(report.getId(), scenario.getProjectId(), t.getId());
            reports.add(report);
        }
        return Result.ok(reports);
    }

    @GetMapping("/reports")
    public Result<List<UiReportEntity>> listReports(@RequestParam String projectId) {
        List<UiReportEntity> reports = uiReportMapper.selectList(
                new LambdaQueryWrapper<UiReportEntity>()
                        .eq(UiReportEntity::getProjectId, projectId)
                        .orderByDesc(UiReportEntity::getStartedAt)
                        .last("LIMIT 100"));
        for (UiReportEntity r : reports) {
            if (r.getDetails() != null && r.getDetails().startsWith("minio://")) {
                r.setDetails(ossService.get(r.getDetails()));
            }
        }
        return Result.ok(reports);
    }

    @GetMapping("/reports/{id}")
    public Result<UiReportEntity> getReport(@PathVariable String id) {
        UiReportEntity r = uiReportMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("报告不存在");
        }
        if (r.getDetails() != null && r.getDetails().startsWith("minio://")) {
            r.setDetails(ossService.get(r.getDetails()));
        }
        return Result.ok(r);
    }

    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@PathVariable String id) {
        uiReportMapper.deleteById(id);
        return Result.ok();
    }

    @SuppressWarnings("unchecked")
    private void saveScenarioSteps(String scenarioId, Object stepsObj) {
        if (!(stepsObj instanceof List<?> steps)) {
            return;
        }
        int order = 0;
        for (Object item : steps) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            UiScenarioStepEntity step = new UiScenarioStepEntity();
            step.setScenarioId(scenarioId);
            step.setSortOrder(order++);
            step.setUiTestCaseId(m.get("uiTestCaseId") == null ? null
                    : String.valueOf(m.get("uiTestCaseId")));
            uiScenarioStepMapper.insert(step);
        }
    }
}
