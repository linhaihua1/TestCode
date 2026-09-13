package com.apiweb.controller;

import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.engine.TriggerType;
import com.apiweb.entity.CiPipelineEntity;
import com.apiweb.entity.CiPipelineRunEntity;
import com.apiweb.service.CiPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * CI/CD 免登录触发接口：供 Jenkins / GitLab CI / GitHub Actions / Shell 脚本调用。
 *
 * <h3>为什么需要独立入口</h3>
 * CI 系统通常不方便维护平台的用户登录态，因此这里采用「流水线级 Token」鉴权：
 * 每个流水线有独立的 webhook Token，泄露范围可控，可随时重置。
 *
 * <h3>调用方式</h3>
 * <ol>
 *   <li>触发：{@code POST /api/v1/webhook/ci/pipelines/trigger?token=xxx&triggerBy=jenkins-1&wait=false}</li>
 *   <li>轮询：{@code GET  /api/v1/webhook/ci/runs/{runId}?token=xxx}，
 *       直到 {@code status} 变为 success / failed / error</li>
 *   <li>判定：{@code gatePassed=1} 表示通过质量门禁，CI 可据此决定构建是否成功</li>
 * </ol>
 *
 * <p>路径已在 {@code apiweb.security.permit-paths} 中放行（默认含 {@code /api/v1/webhook/ci/**}），
 * 不经过 JWT 拦截器。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhook/ci")
@RequiredArgsConstructor
public class CiWebhookController {

    private final CiPipelineService pipelineService;

    /**
     * 触发流水线执行（免登录，凭流水线 Token 鉴权）。
     *
     * @param token     流水线的 webhook Token（query 参数，必填）
     * @param triggerBy 触发者标识（Jenkins BUILD_TAG / commit sha / 流水线名等，可选）
     * @param wait      是否阻塞等待执行完成：true=同步返回终态（用例多时可能超时）；
     *                  false（默认）=立即返回，调用方按 runId 轮询
     * @return 执行记录（含 id 供轮询、status、summary、gatePassed）
     * @throws BizException token 缺失抛 badRequest；无效抛 notFound；未启用抛 forbidden
     */
    @PostMapping("/pipelines/trigger")
    public Result<CiPipelineRunEntity> trigger(@RequestParam String token,
                                               @RequestParam(required = false) String triggerBy,
                                               @RequestParam(required = false, defaultValue = "false") boolean wait) {
        if (token == null || token.isBlank()) {
            throw BizException.badRequest("token 缺失");
        }
        CiPipelineEntity pipeline = pipelineService.getByToken(token);
        String by = triggerBy == null || triggerBy.isBlank() ? TriggerType.WEBHOOK.getCode() : triggerBy;
        log.info("CI 触发流水线: pipelineId={}, triggerBy={}, wait={}", pipeline.getId(), by, wait);
        return Result.ok(pipelineService.trigger(pipeline.getId(), by, wait, TriggerType.WEBHOOK));
    }

    /**
     * 查询执行结果（免登录，凭流水线 Token 鉴权）。
     * <p>校验方式：先按 runId 取执行记录，再校验其所属流水线的 Token 是否匹配，
     * 避免越权查询其它流水线的执行记录。
     *
     * @param runId 执行记录 ID（触发接口返回的 data.id）
     * @param token 流水线 webhook Token
     * @return 执行记录（status / summary / gateResult / gatePassed / jenkinsBuildUrl 等）
     * @throws BizException token 无效抛 notFound；Token 与执行记录不匹配抛 forbidden
     */
    @GetMapping("/runs/{runId}")
    public Result<CiPipelineRunEntity> run(@PathVariable String runId, @RequestParam String token) {
        if (token == null || token.isBlank()) {
            throw BizException.badRequest("token 缺失");
        }
        CiPipelineEntity pipeline = pipelineService.getByToken(token);
        CiPipelineRunEntity run = pipelineService.getRun(runId);
        if (!pipeline.getId().equals(run.getPipelineId())) {
            throw BizException.forbidden("该执行记录不属于此流水线");
        }
        return Result.ok(run);
    }
}
