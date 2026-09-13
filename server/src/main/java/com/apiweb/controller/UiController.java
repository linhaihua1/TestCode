package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.engine.EngineDtos;
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
import com.apiweb.service.OssService;
import com.apiweb.service.UiExecutionService;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.apiweb.engine.RunStatus;

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
    private final UiExecutionService uiExecutionService;

    /**
     * 是否启用 RabbitMQ 消费端。localdev 下为 false，此时 run 接口改为 @Async 异步执行，
     * 避免报告永远停留在 pending。
     */
    @Value("${apiweb.mq.enabled:true}")
    private boolean mqListenerEnabled;

    // ---------------- UI 测试用例 ----------------

    /**
     * 查询项目的 UI 测试用例列表（按更新时间倒序）。
     *
     * @param projectId 项目 ID（必填）
     * @return UI 用例列表
     */
    @GetMapping("/tests")
    public Result<List<UiTestCaseEntity>> listTests(@RequestParam String projectId) {
        return Result.ok(uiTestCaseMapper.selectList(
                new LambdaQueryWrapper<UiTestCaseEntity>()
                        .eq(UiTestCaseEntity::getProjectId, projectId)
                        .orderByDesc(UiTestCaseEntity::getUpdatedAt)));
    }

    /**
     * 获取 UI 用例详情。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 用例实体（含 setup/steps/teardown 三段）
     * @throws BizException 用例不存在时抛 notFound
     */
    @GetMapping("/tests/{id}")
    public Result<UiTestCaseEntity> getTest(@PathVariable String id) {
        UiTestCaseEntity t = uiTestCaseMapper.selectById(id);
        if (t == null) {
            throw BizException.notFound("UI 用例不存在");
        }
        return Result.ok(t);
    }

    /**
     * 新建 UI 用例：补齐 setup/steps/teardown 默认空数组后落库。
     *
     * @param t UI 用例实体（请求体），name 必填
     * @return 已落库用例实体
     * @throws BizException 用例名为空时抛 badRequest
     * @audit 写审计日志：action=create, entityType=ui_test
     */
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

    /**
     * 更新 UI 用例（覆盖可写字段）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @param t  用例实体（请求体）
     * @return 无业务数据
     * @throws BizException 用例不存在时抛 notFound
     * @audit 写审计日志：action=update, entityType=ui_test
     */
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

    /**
     * 删除 UI 用例（物理删除）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=ui_test
     */
    @AuditLog(action = "delete", entityType = "ui_test")
    @DeleteMapping("/tests/{id}")
    public Result<Void> deleteTest(@PathVariable String id) {
        uiTestCaseMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- UI 执行场景 ----------------

    /**
     * 查询项目的 UI 执行场景列表（按更新时间倒序）。
     *
     * @param projectId 项目 ID（必填）
     * @return UI 场景列表
     */
    @GetMapping("/scenarios")
    public Result<List<UiScenarioEntity>> listScenarios(@RequestParam String projectId) {
        return Result.ok(uiScenarioMapper.selectList(
                new LambdaQueryWrapper<UiScenarioEntity>()
                        .eq(UiScenarioEntity::getProjectId, projectId)
                        .orderByDesc(UiScenarioEntity::getUpdatedAt)));
    }

    /**
     * 获取 UI 场景详情（含步骤列表，按排序升序）。
     *
     * @param id 场景 ID（路径参数，必填）
     * @return { scenario: 场景实体, steps: 步骤列表 }
     * @throws BizException 场景不存在时抛 notFound
     */
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

    /**
     * 新建 UI 场景（含步骤与执行配置），整库写操作包裹事务。
     *
     * @param body 请求体，含 projectId（必填）、name（默认"未命名场景"）、description、steps、executor/retry/timeout 等配置
     * @return 已落库的场景实体（不含步骤明细）
     * @audit 写审计日志：action=create, entityType=ui_scenario
     */
    @AuditLog(action = "create", entityType = "ui_scenario")
    @Transactional
    @PostMapping("/scenarios")
    public Result<UiScenarioEntity> createScenario(@RequestBody Map<String, Object> body) {
        UiScenarioEntity scenario = new UiScenarioEntity();
        scenario.setProjectId(String.valueOf(body.get("projectId")));
        scenario.setName(String.valueOf(body.getOrDefault("name", "未命名场景")));
        scenario.setDescription(body.get("description") == null ? null
                : String.valueOf(body.get("description")));
        applyScenarioConfig(scenario, body);
        uiScenarioMapper.insert(scenario);
        saveScenarioSteps(scenario.getId(), body.get("steps"));
        return Result.ok(scenario);
    }

    /**
     * 更新 UI 场景（含可选步骤整体替换与执行配置），整库写操作包裹事务。
     * <p>仅覆盖传了的 name/description；若传了 steps，则先删旧步骤再整批插入。
     *
     * @param id   场景 ID（路径参数，必填）
     * @param body 请求体，可含 name/description/steps/executor 配置
     * @return 无业务数据
     * @throws BizException 场景不存在时抛 notFound
     * @audit 写审计日志：action=update, entityType=ui_scenario
     */
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
        if (body.containsKey("description")) {
            scenario.setDescription(body.get("description") == null ? null
                    : String.valueOf(body.get("description")));
        }
        applyScenarioConfig(scenario, body);
        uiScenarioMapper.updateById(scenario);
        if (body.containsKey("steps")) {
            uiScenarioStepMapper.delete(new LambdaQueryWrapper<UiScenarioStepEntity>()
                    .eq(UiScenarioStepEntity::getScenarioId, id));
            saveScenarioSteps(id, body.get("steps"));
        }
        return Result.ok();
    }

    /**
     * 删除 UI 场景：先删步骤再删主表（物理删除），整库写操作包裹事务。
     *
     * @param id 场景 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=ui_scenario
     */
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
     * 触发 UI 用例执行：创建 PENDING 报告并投递 MQ 异步执行（localdev 无消费端时改用 @Async 当前 JVM 内执行）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 新建的报告实体（status=pending）
     * @throws BizException 用例不存在时抛 notFound
     * @audit 写审计日志：action=execute, entityType=ui_test
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
        report.setStatus(RunStatus.PENDING.getCode());
        report.setStartedAt(Instant.now());
        report.setDetails("[]");
        uiReportMapper.insert(report);
        if (mqListenerEnabled) {
            taskProducer.sendUiTask(report.getId(), t.getProjectId(), id);
        } else {
            // localdev：消费端关闭，改用 @Async 异步执行，保证报告能被回写且不阻塞请求
            uiExecutionService.executeAsync(new EngineDtos.TaskMessage(
                    TaskType.UI.getCode(), report.getId(), t.getProjectId(), id, null, null, null));
        }
        return Result.ok(report);
    }

    /**
     * 触发 UI 场景执行：按步骤顺序为每个关联用例创建 PENDING 报告并投递 MQ，携带 executor/重试/超时等扩展参数。
     *
     * @param id 场景 ID（路径参数，必填）
     * @return 本次触发产生的报告实体列表
     * @throws BizException 场景不存在时抛 notFound
     * @audit 写审计日志：action=execute, entityType=ui_scenario
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
        // 组装执行机/重试/超时扩展参数
        Map<String, Object> extra = new java.util.HashMap<>();
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
            report.setStatus(RunStatus.PENDING.getCode());
            report.setStartedAt(Instant.now());
            report.setDetails("[]");
            uiReportMapper.insert(report);
            if (mqListenerEnabled) {
                taskProducer.sendUiTask(report.getId(), scenario.getProjectId(), t.getId(),
                        scenario.getEnvironmentId(), extra);
            } else {
                uiExecutionService.executeAsync(new EngineDtos.TaskMessage(
                        TaskType.UI.getCode(), report.getId(), scenario.getProjectId(), t.getId(),
                        null, scenario.getEnvironmentId(), extra));
            }
            reports.add(report);
        }
        return Result.ok(reports);
    }

    /**
     * 查询项目的 UI 报告列表（按开始时间倒序）。
     * <p>details 字段若为对象存储引用（minio:// 前缀）则实时拉取回填；limit 控制返回条数上限避免一次回传过多。
     *
     * @param projectId 项目 ID（必填）
     * @param limit     返回条数上限（默认取配置项 apiweb.engine.list-max-limit，最小 1）
     * @return UI 报告列表（details 已解析为实际内容）
     */
    @GetMapping("/reports")
    public Result<List<UiReportEntity>> listReports(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "${apiweb.engine.list-max-limit:100}") int limit) {
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
        return Result.ok(reports);
    }

    /**
     * 获取 UI 报告详情（details 若为 minio 引用则实时拉取回填）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 报告实体（含执行步骤明细 details）
     * @throws BizException 报告不存在时抛 notFound
     */
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

    /**
     * 删除 UI 报告（物理删除）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 无业务数据
     */
    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@PathVariable String id) {
        uiReportMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 从请求体解析执行参数并写入场景实体。
     */
    private void applyScenarioConfig(UiScenarioEntity scenario, Map<String, Object> body) {
        if (body.containsKey("environmentId")) {
            scenario.setEnvironmentId(body.get("environmentId") == null ? null
                    : String.valueOf(body.get("environmentId")));
        }
        if (body.containsKey("variables")) {
            scenario.setVariables(body.get("variables") == null ? null
                    : JsonUtils.toJson(body.get("variables")));
        }
        if (body.containsKey("executorUrl")) {
            scenario.setExecutorUrl(body.get("executorUrl") == null ? null
                    : String.valueOf(body.get("executorUrl")));
        }
        if (body.containsKey("executorHost")) {
            scenario.setExecutorHost(body.get("executorHost") == null ? null
                    : String.valueOf(body.get("executorHost")));
        }
        if (body.containsKey("executorPort")) {
            scenario.setExecutorPort(body.get("executorPort") == null ? null
                    : ((Number) body.get("executorPort")).intValue());
        }
        if (body.containsKey("retryCount")) {
            scenario.setRetryCount(body.get("retryCount") == null ? 0
                    : ((Number) body.get("retryCount")).intValue());
        }
        if (body.containsKey("timeoutMs")) {
            scenario.setTimeoutMs(body.get("timeoutMs") == null ? 0
                    : ((Number) body.get("timeoutMs")).intValue());
        }
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
