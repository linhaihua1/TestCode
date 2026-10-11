package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.engine.TaskExecutorService;
import com.apiweb.engine.TriggerType;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.vo.webhook.WebhookTriggerVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CI/CD webhook 触发业务服务。
 *
 * <p>安全机制：Token 严格匹配、任务须启用 webhook、5 秒频率限制。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final TestTaskMapper testTaskMapper;
    private final TaskExecutorService taskExecutorService;

    /**
     * 触发任务执行（供 CI/CD 系统调用，免登录，仅凭 token 校验）。
     */
    public WebhookTriggerVO trigger(String token, String triggerBy) {
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
        if (!canTrigger(task.getId())) {
            throw BizException.badRequest("任务触发过于频繁,请 5 秒后再试");
        }

        String execId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("CI/CD 触发任务执行: taskId={}, execId={}, triggerBy={}", task.getId(), execId, triggerBy);

        List<TestTaskRunEntity> runs = taskExecutorService.execute(task);

        WebhookTriggerVO resp = new WebhookTriggerVO();
        resp.setTaskId(task.getId());
        resp.setTaskName(task.getName());
        resp.setExecId(execId);
        resp.setTriggeredAt(Instant.now().toString());
        resp.setTriggeredBy(triggerBy == null ? TriggerType.WEBHOOK.getCode() : triggerBy);
        resp.setRunCount(runs == null ? 0 : runs.size());
        resp.setRunIds(runs == null ? List.of() : runs.stream().map(TestTaskRunEntity::getId).toList());
        return resp;
    }

    private boolean canTrigger(String taskId) {
        List<TestTaskRunEntity> recent = taskExecutorService.listRuns(taskId);
        if (recent.isEmpty()) {
            return true;
        }
        Instant lastStart = recent.get(0).getStartedAt();
        if (lastStart == null) {
            return true;
        }
        return lastStart.isBefore(Instant.now().minusSeconds(5));
    }
}
