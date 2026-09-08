package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.engine.TaskExecutorService;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CI/CD 触发：提供给 GitHub Actions / GitLab CI / Jenkins / 自研调度系统调用。
 *
 * <h3>使用流程</h3>
 * <ol>
 *   <li>用户在 UI 创建任务,生成 webhookToken（API 响应中返回一次）</li>
 *   <li>在 CI 系统中添加调用：{@code POST /api/v1/webhook/test-tasks/trigger?token=xxx}</li>
 *   <li>CI 系统按返回 runId 轮询报告或订阅回调通知</li>
 * </ol>
 *
 * <h3>安全机制</h3>
 * <ul>
 *   <li>Token 必须严格匹配（32 位随机串）</li>
 *   <li>任务必须 {@code webhookEnabled=true}</li>
 *   <li>触发频率限制：同一任务 5 秒内只允许一次（防误触发）</li>
 *   <li>触发记录写入审计日志</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final TestTaskMapper testTaskMapper;
    private final TaskExecutorService taskExecutorService;

    /**
     * 触发任务执行。
     *
     * @param token  任务的 webhook token（query 参数,方便命令行调用）
     * @param triggerBy 触发者标识（CI job id / commit sha 等,可选）
     * @return 创建的 run 列表
     */
    @PostMapping("/test-tasks/trigger")
    @AuditLog(action = "webhook_trigger", entityType = "test_task")
    public Result<TriggerResponse> trigger(@RequestParam String token,
                                            @RequestParam(required = false) String triggerBy) {
        if (token == null || token.isBlank()) {
            throw BizException.badRequest("token 缺失");
        }
        TestTaskEntity task = testTaskMapper.selectOne(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getWebhookToken, token)
                        .isNull(TestTaskEntity::getDeletedAt));
        if (task == null) {
            throw BizException.notFound("无效的 token");
        }
        if (!Boolean.TRUE.equals(task.getWebhookEnabled())) {
            throw BizException.forbidden("该任务未启用 webhook 触发");
        }

        // 频率限制：同任务 5 秒内不允许多次触发
        if (!canTrigger(task.getId())) {
            throw BizException.badRequest("任务触发过于频繁,请 5 秒后再试");
        }

        // 同步触发执行（不通过 RabbitMQ,直接走本地并行池）
        // 即使 task 创建时 webhookAutoExecute=false,这里也保留"显式创建 run"的语义
        String execId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("CI/CD 触发任务执行: taskId={}, execId={}, triggerBy={}",
                task.getId(), execId, triggerBy);

        List<TestTaskRunEntity> runs = taskExecutorService.execute(task);

        TriggerResponse resp = new TriggerResponse();
        resp.taskId = task.getId();
        resp.taskName = task.getName();
        resp.execId = execId;
        resp.triggeredAt = Instant.now().toString();
        resp.triggeredBy = triggerBy == null ? "webhook" : triggerBy;
        resp.runCount = runs == null ? 0 : runs.size();
        resp.runIds = runs == null ? List.of() : runs.stream().map(TestTaskRunEntity::getId).toList();
        return Result.ok(resp);
    }

    /**
     * 简单的速率限制：检查最近一次 run 的 startedAt 是否在 5 秒内。
     */
    private boolean canTrigger(String taskId) {
        List<TestTaskRunEntity> recent = taskExecutorService.listRuns(taskId);
        if (recent.isEmpty()) return true;
        Instant lastStart = recent.get(0).getStartedAt();
        if (lastStart == null) return true;
        return lastStart.isBefore(Instant.now().minusSeconds(5));
    }

    /**
     * 触发响应：返回 execId + runIds,便于 CI 系统后续轮询报告。
     */
    @Data
    public static class TriggerResponse {
        private String taskId;
        private String taskName;
        /** 本次执行的会话 ID（16 位） */
        private String execId;
        private String triggeredAt;
        private String triggeredBy;
        private int runCount;
        private List<String> runIds;
    }
}