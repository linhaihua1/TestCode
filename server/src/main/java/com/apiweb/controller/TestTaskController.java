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
import com.apiweb.engine.RunStatus;

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

    /**
     * 查询项目的测试任务列表（仅未删除，按更新时间倒序）。
     *
     * @param projectId 项目 ID（必填）
     * @return 测试任务列表
     */
    @GetMapping
    public Result<List<TestTaskEntity>> list(@RequestParam String projectId) {
        return Result.ok(testTaskMapper.selectList(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getProjectId, projectId)
                        .isNull(TestTaskEntity::getDeletedAt) // 过滤已软删除任务
                        .orderByDesc(TestTaskEntity::getUpdatedAt)));
    }

    /**
     * 获取测试任务详情。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 任务实体（含 caseIds/executeMode/cron/webhook 等配置）
     * @throws BizException 任务不存在时抛 notFound
     */
    @GetMapping("/{id}")
    public Result<TestTaskEntity> get(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return Result.ok(task);
    }

    /**
     * 新建测试任务：补齐默认值并校验后落库，同时注册定时调度（若配置了 cron）。
     *
     * @param task 任务实体（请求体），name 必填，executeMode 仅限 sequential/parallel
     * @return 已落库的任务实体
     * @throws BizException 必填缺失或 executeMode/failStrategy/cron 不合法时抛 badRequest
     * @audit 写审计日志：action=create, entityType=test_task
     */
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

    /**
     * 更新测试任务：未传字段回填原值（防止被默认值清空），校验后落库并重新注册定时调度。
     *
     * @param id   任务 ID（路径参数，必填）
     * @param task 任务实体（请求体）
     * @return 无业务数据
     * @throws BizException 任务不存在或字段不合法时抛 notFound/badRequest
     * @audit 写审计日志：action=update, entityType=test_task
     */
    @AuditLog(action = "update", entityType = "test_task")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody TestTaskEntity task) {
        TestTaskEntity origin = testTaskMapper.selectById(id);
        if (origin == null) {
            throw BizException.notFound("任务不存在");
        }
        task.setId(id);
        // 未传字段回填原值，避免 updateById 时被默认值覆盖清空
        mergeDefaults(task, origin);
        validate(task);
        testTaskMapper.updateById(task);
        taskScheduleService.schedule(task);
        return Result.ok();
    }

    /**
     * 软删除测试任务：打 deletedAt 并移除其定时调度（不再触发）。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 无业务数据（不存在时静默成功）
     * @audit 写审计日志：action=delete, entityType=test_task
     */
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
     * 异步触发（投递 RabbitMQ，适合分布式集群）：为每个用例创建 PENDING 运行记录并发送 MQ，分布到执行机执行。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 本次触发的运行记录（TestTaskRun）列表
     * @throws BizException 任务不存在时抛 notFound
     * @audit 写审计日志：action=execute, entityType=test_task
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
            run.setResult(RunStatus.PENDING.getCode());
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
     * 同步本地执行（走 TaskExecutorService，支持并行池 + 失败策略），接口阻塞直到全部用例执行完毕。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 执行批次列表（每条用例含结果与断言）
     * @throws BizException 任务不存在时抛 notFound
     * @audit 写审计日志：action=execute_sync, entityType=test_task
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

    /**
     * 查询某任务的历史执行批次列表（details 若为 minio 引用则实时拉取回填为实际内容）。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 执行批次（TestTaskRun）列表
     */
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

    /**
     * 启用/停用任务：切换 enabled 标记并同步注册或移除定时调度。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 无业务数据
     * @throws BizException 任务不存在时抛 notFound
     */
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
     * 生成/重置 webhook token（仅返回一次，前端需提示用户妥善保管）。
     * <p>rotate 隐含"启用 webhook"意图：强制开启 webhookEnabled，避免此前禁用后无法重新启用。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return token 与完整触发 URL
     * @throws BizException 任务不存在时抛 notFound
     * @audit 写审计日志：action=rotate_webhook, entityType=test_task
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
        // rotate 即意图启用 webhook：强制开启，避免此前 disable 后无法重新启用
        task.setWebhookEnabled(true);
        if (task.getWebhookAutoExecute() == null) task.setWebhookAutoExecute(true);
        testTaskMapper.updateById(task);
        WebhookTokenResponse resp = new WebhookTokenResponse();
        resp.token = token;
        resp.url = "/api/v1/webhook/test-tasks/trigger?token=" + token;
        return Result.ok(resp);
    }

    /**
     * 禁用任务的 webhook 触发（保留已生成的 token，仅关闭触发开关）。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 无业务数据
     * @throws BizException 任务不存在时抛 notFound
     * @audit 写审计日志：action=disable_webhook, entityType=test_task
     */
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

    /**
     * 更新时合并：未传字段回填原值（区别于 initDefaults 的"填默认值"）。
     * 避免前端只传部分字段时，被 initDefaults 用空默认值覆盖清空原有数据。
     */
    private void mergeDefaults(TestTaskEntity task, TestTaskEntity origin) {
        if (task.getCaseIds() == null) task.setCaseIds(origin.getCaseIds());
        if (task.getVariables() == null) task.setVariables(origin.getVariables());
        if (task.getNotifyChannels() == null) task.setNotifyChannels(origin.getNotifyChannels());
        if (task.getExecuteMode() == null) task.setExecuteMode(origin.getExecuteMode());
        if (task.getFailStrategy() == null) task.setFailStrategy(origin.getFailStrategy());
        if (task.getParallelPoolSize() == null) task.setParallelPoolSize(origin.getParallelPoolSize());
        if (task.getRetryCount() == null) task.setRetryCount(origin.getRetryCount());
        if (task.getTimeoutMs() == null) task.setTimeoutMs(origin.getTimeoutMs());
        if (task.getEnabled() == null) task.setEnabled(origin.getEnabled());
        if (task.getWebhookEnabled() == null) task.setWebhookEnabled(origin.getWebhookEnabled());
        if (task.getWebhookAutoExecute() == null) task.setWebhookAutoExecute(origin.getWebhookAutoExecute());
        if (task.getEnvironmentId() == null) task.setEnvironmentId(origin.getEnvironmentId());
        if (task.getCronExpr() == null) task.setCronExpr(origin.getCronExpr());
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