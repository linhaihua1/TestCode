package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.engine.TaskExecutorService;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.TestTaskRunMapper;
import com.apiweb.mq.TaskProducer;
import com.apiweb.security.UserContext;
import com.apiweb.service.OssService;
import com.apiweb.service.TaskScheduleService;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

/**
 * 测试任务：编排用例集合，支持手动触发（RabbitMQ 异步）/ 本地并行执行 / Quartz 定时调度 / CI/CD webhook 触发。
 */
@RestController
@RequestMapping("/api/v1/test-tasks")
@RequiredArgsConstructor
public class TestTaskController {

    private final TestTaskMapper testTaskMapper;
    private final TestTaskRunMapper testTaskRunMapper;
    private final TaskProducer taskProducer;
    private final TaskScheduleService taskScheduleService;
    private final TaskExecutorService taskExecutorService;
    private final OssService ossService;

    @GetMapping
    public Result<List<TestTaskEntity>> list(@RequestParam String projectId) {
        return Result.ok(testTaskMapper.selectList(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getProjectId, projectId)
                        .isNull(TestTaskEntity::getDeletedAt)
                        .orderByDesc(TestTaskEntity::getUpdatedAt)));
    }

    @GetMapping("/{id}")
    public Result<TestTaskEntity> get(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return Result.ok(task);
    }

    @AuditLog(action = "create", entityType = "test_task")
    @PostMapping
    public Result<TestTaskEntity> create(@RequestBody TestTaskEntity task) {
        initDefaults(task);
        validate(task);
        task.setCreatedBy(UserContext.username());
        testTaskMapper.insert(task);
        taskScheduleService.schedule(task);
        return Result.ok(task);
    }

    @AuditLog(action = "update", entityType = "test_task")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody TestTaskEntity task) {
        if (testTaskMapper.selectById(id) == null) {
            throw BizException.notFound("任务不存在");
        }
        task.setId(id);
        initDefaults(task);
        validate(task);
        testTaskMapper.updateById(task);
        taskScheduleService.schedule(task);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "test_task")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task != null) {
            task.setDeletedAt(Instant.now());
            testTaskMapper.updateById(task);
        }
        taskScheduleService.remove(id);
        return Result.ok();
    }

    /**
     * 异步触发（投递 RabbitMQ,适合分布式集群）。
     */
    @AuditLog(action = "execute", entityType = "test_task")
    @PostMapping("/{id}/run")
    public Result<List<TestTaskRunEntity>> run(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        List<String> caseIds = JsonUtils.fromJson(
                task.getCaseIds() == null ? "[]" : task.getCaseIds(), List.class);
        List<TestTaskRunEntity> runs = new java.util.ArrayList<>();
        for (String caseId : caseIds) {
            TestTaskRunEntity run = new TestTaskRunEntity();
            run.setTaskId(id);
            run.setResult("pending");
            run.setStartedAt(Instant.now());
            run.setDetails("[]");
            testTaskRunMapper.insert(run);
            taskProducer.sendApiTask(run.getId(), task.getProjectId(), id, caseId,
                    task.getEnvironmentId());
            runs.add(run);
        }
        return Result.ok(runs);
    }

    /**
     * 同步本地执行（走 TaskExecutorService,支持并行池 + 失败策略）。
     */
    @AuditLog(action = "execute_sync", entityType = "test_task")
    @PostMapping("/{id}/run-sync")
    public Result<List<TestTaskRunEntity>> runSync(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return Result.ok(taskExecutorService.execute(task));
    }

    @GetMapping("/{id}/runs")
    public Result<List<TestTaskRunEntity>> runs(@PathVariable String id) {
        List<TestTaskRunEntity> list = taskExecutorService.listRuns(id);
        for (TestTaskRunEntity run : list) {
            if (run.getDetails() != null && run.getDetails().startsWith("minio://")) {
                run.setDetails(ossService.get(run.getDetails()));
            }
        }
        return Result.ok(list);
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        testTaskMapper.updateById(task);
        taskScheduleService.schedule(task);
        return Result.ok();
    }

    /**
     * 生成/重置 webhook token。
     * 仅返回一次,前端需提示用户妥善保管。
     */
    @AuditLog(action = "rotate_webhook", entityType = "test_task")
    @PostMapping("/{id}/webhook/rotate")
    public Result<WebhookTokenResponse> rotateWebhook(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        String token = randomToken(32);
        task.setWebhookToken(token);
        if (task.getWebhookEnabled() == null) task.setWebhookEnabled(true);
        if (task.getWebhookAutoExecute() == null) task.setWebhookAutoExecute(true);
        testTaskMapper.updateById(task);
        WebhookTokenResponse resp = new WebhookTokenResponse();
        resp.token = token;
        resp.url = "/api/v1/webhook/test-tasks/trigger?token=" + token;
        return Result.ok(resp);
    }

    @AuditLog(action = "disable_webhook", entityType = "test_task")
    @PostMapping("/{id}/webhook/disable")
    public Result<Void> disableWebhook(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        task.setWebhookEnabled(false);
        testTaskMapper.updateById(task);
        return Result.ok();
    }

    @Data
    public static class WebhookTokenResponse {
        private String token;
        private String url;
    }

    // ====================================================
    // 内部方法
    // ====================================================

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private void initDefaults(TestTaskEntity task) {
        if (task.getCaseIds() == null) task.setCaseIds("[]");
        if (task.getVariables() == null) task.setVariables("[]");
        if (task.getNotifyChannels() == null) task.setNotifyChannels("[]");
        if (task.getExecuteMode() == null) task.setExecuteMode("sequential");
        if (task.getFailStrategy() == null) task.setFailStrategy("stop_on_fail");
        if (task.getParallelPoolSize() == null) task.setParallelPoolSize(5);
        if (task.getRetryCount() == null) task.setRetryCount(0);
        if (task.getTimeoutMs() == null) task.setTimeoutMs(300_000);
        if (task.getEnabled() == null) task.setEnabled(true);
        if (task.getWebhookEnabled() == null) task.setWebhookEnabled(false);
        if (task.getWebhookAutoExecute() == null) task.setWebhookAutoExecute(true);
    }

    private void validate(TestTaskEntity task) {
        if (task.getName() == null || task.getName().isBlank()) {
            throw BizException.badRequest("任务名称必填");
        }
        if (!"sequential".equalsIgnoreCase(task.getExecuteMode())
                && !"parallel".equalsIgnoreCase(task.getExecuteMode())) {
            throw BizException.badRequest("executeMode 必须是 sequential 或 parallel");
        }
        if (!List.of("stop_on_fail", "continue_all", "retry_then_stop")
                .contains(task.getFailStrategy())) {
            throw BizException.badRequest("failStrategy 不合法");
        }
        if (task.getParallelPoolSize() != null
                && (task.getParallelPoolSize() < 1 || task.getParallelPoolSize() > 200)) {
            throw BizException.badRequest("parallelPoolSize 必须在 1-200 之间");
        }
        if (task.getCronExpr() != null && !task.getCronExpr().isBlank()) {
            // 简单 cron 校验：5 段（分 时 日 月 周）
            String[] parts = task.getCronExpr().trim().split("\\s+");
            if (parts.length != 5 && parts.length != 6 && parts.length != 7) {
                throw BizException.badRequest("cron 表达式必须是 5/6/7 段");
            }
        }
    }
}