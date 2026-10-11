package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.service.WebhookService;
import com.apiweb.vo.webhook.WebhookTriggerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * 触发任务执行（供 CI/CD 系统调用，免登录，仅凭 token 校验）。
     *
     * @param token     任务的 webhook token（query 参数，必填）
     * @param triggerBy 触发者标识（可选）
     * @return 触发响应：含 execId 会话 ID 与 runIds 列表
     * @audit 写审计日志：action=webhook_trigger, entityType=test_task
     */
    @PostMapping("/test-tasks/trigger")
    @AuditLog(action = "webhook_trigger", entityType = "test_task")
    public Result<WebhookTriggerVO> trigger(@RequestParam String token,
                                            @RequestParam(required = false) String triggerBy) {
        return Result.ok(webhookService.trigger(token, triggerBy));
    }
}
