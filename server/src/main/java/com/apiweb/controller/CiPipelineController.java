package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.engine.PipelineTargetType;
import com.apiweb.engine.PipelineTestType;
import com.apiweb.engine.TriggerType;
import com.apiweb.entity.CiPipelineEntity;
import com.apiweb.entity.CiPipelineRunEntity;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.UiScenarioEntity;
import com.apiweb.entity.UiTestCaseEntity;
import com.apiweb.mapper.PerfCaseMapper;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.UiScenarioMapper;
import com.apiweb.mapper.UiTestCaseMapper;
import com.apiweb.security.UserContext;
import com.apiweb.service.CiPipelineService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CI/CD 流水线管理：把接口自动化 / UI 自动化 / 性能测试的执行目标包装成可被
 * Jenkins（或其它 CI 系统）触发的流水线，并维护执行记录与质量门禁结果。
 *
 * <h3>典型使用流程</h3>
 * <ol>
 *   <li>在「Jenkins 服务器」中配置好 Jenkins 实例（管理员）</li>
 *   <li>新建流水线：选择测试类型（api/ui/perf）+ 执行目标 + 执行方式（平台执行 / Jenkins 执行）+ 质量门禁</li>
 *   <li>点击「生成 Token」拿到 CI 触发地址，或点击「Jenkinsfile」复制脚本粘贴到 Jenkins</li>
 *   <li>Jenkins 构建时触发流水线，平台执行用例并判定门禁，构建按结果成功/失败</li>
 * </ol>
 *
 * <p>viewer 角色的写操作由 {@code RoleAspect} 按 HTTP 方法统一拦截，此处不再重复标注。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ci/pipelines")
@RequiredArgsConstructor
public class CiPipelineController {

    private final CiPipelineService pipelineService;
    private final TestTaskMapper testTaskMapper;
    private final UiScenarioMapper uiScenarioMapper;
    private final UiTestCaseMapper uiTestCaseMapper;
    private final PerfCaseMapper perfCaseMapper;

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 查询项目的流水线列表（仅未删除，按更新时间倒序）。
     *
     * @param projectId 项目 ID（必填）
     * @return 流水线列表
     */
    @GetMapping
    public Result<List<CiPipelineEntity>> list(@RequestParam String projectId) {
        return Result.ok(pipelineService.list(projectId));
    }

    /**
     * 获取流水线详情。
     *
     * @param id 流水线 ID
     * @return 流水线实体（含质量门禁、Jenkins 配置）
     * @throws BizException 不存在时抛 notFound
     */
    @GetMapping("/{id}")
    public Result<CiPipelineEntity> get(@PathVariable String id) {
        return Result.ok(pipelineService.get(id));
    }

    /**
     * 查询某测试类型下可选的执行目标（供前端下拉选择）。
     *
     * @param projectId 项目 ID（必填）
     * @param testType  测试类型：api / ui / perf（默认 api）
     * @return 目标列表：[{id, name, type}]（已过滤软删除）
     */
    @GetMapping("/targets")
    public Result<List<Map<String, String>>> targets(@RequestParam String projectId,
                                                     @RequestParam(defaultValue = "api") String testType) {
        PipelineTestType type = PipelineTestType.parseOrDefault(testType);
        List<Map<String, String>> result = new java.util.ArrayList<>();
        switch (type) {
            case API -> testTaskMapper.selectList(
                    new LambdaQueryWrapper<TestTaskEntity>()
                            .eq(TestTaskEntity::getProjectId, projectId)
                            .isNull(TestTaskEntity::getDeletedAt)
                            .orderByDesc(TestTaskEntity::getUpdatedAt))
                    .forEach(t -> result.add(target(t.getId(), t.getName(), PipelineTargetType.TEST_TASK)));
            case UI -> {
                // UI 场景与 UI 用例都可执行，前端按 targetType 区分
                // 注：t_ui_scenario / t_ui_test_case 无软删除字段，无需追加 deletedAt 过滤
                uiScenarioMapper.selectList(
                        new LambdaQueryWrapper<UiScenarioEntity>()
                                .eq(UiScenarioEntity::getProjectId, projectId)
                                .orderByDesc(UiScenarioEntity::getUpdatedAt))
                        .forEach(s -> result.add(target(s.getId(), s.getName(), PipelineTargetType.UI_SCENARIO)));
                uiTestCaseMapper.selectList(
                        new LambdaQueryWrapper<UiTestCaseEntity>()
                                .eq(UiTestCaseEntity::getProjectId, projectId)
                                .orderByDesc(UiTestCaseEntity::getUpdatedAt))
                        .forEach(t -> result.add(target(t.getId(), t.getName(), PipelineTargetType.UI_TEST)));
            }
            case PERF -> perfCaseMapper.selectList(
                    new LambdaQueryWrapper<PerfCaseEntity>()
                            .eq(PerfCaseEntity::getProjectId, projectId)
                            .isNull(PerfCaseEntity::getDeletedAt)
                            .orderByDesc(PerfCaseEntity::getUpdatedAt))
                    .forEach(c -> result.add(target(c.getId(), c.getName(), PipelineTargetType.PERF_CASE)));
        }
        return Result.ok(result);
    }

    // ==================================================================
    // 增删改
    // ==================================================================

    /**
     * 新建流水线。
     *
     * @param pipeline 流水线实体（projectId / name / testType / targetId 必填）
     * @return 已落库的流水线
     * @audit 写审计日志：action=create, entityType=ci_pipeline
     */
    @AuditLog(action = "create", entityType = "ci_pipeline")
    @PostMapping
    public Result<CiPipelineEntity> create(@RequestBody CiPipelineEntity pipeline) {
        return Result.ok(pipelineService.create(pipeline));
    }

    /**
     * 更新流水线（未传字段保持原值）。
     *
     * @param id       流水线 ID
     * @param pipeline 待更新字段
     * @return 更新后的流水线
     * @audit 写审计日志：action=update, entityType=ci_pipeline
     */
    @AuditLog(action = "update", entityType = "ci_pipeline")
    @PutMapping("/{id}")
    public Result<CiPipelineEntity> update(@PathVariable String id, @RequestBody CiPipelineEntity pipeline) {
        return Result.ok(pipelineService.update(id, pipeline));
    }

    /**
     * 删除流水线（软删除，执行记录保留）。
     *
     * @param id 流水线 ID
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=ci_pipeline
     */
    @AuditLog(action = "delete", entityType = "ci_pipeline")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        pipelineService.delete(id);
        return Result.ok();
    }

    /**
     * 启用 / 停用流水线。
     *
     * @param id 流水线 ID
     * @return 更新后的流水线
     */
    @PostMapping("/{id}/toggle")
    public Result<CiPipelineEntity> toggle(@PathVariable String id) {
        return Result.ok(pipelineService.toggle(id));
    }

    // ==================================================================
    // 触发与执行记录
    // ==================================================================

    /**
     * 触发流水线执行。
     *
     * @param id      流水线 ID
     * @param request 触发参数：{@code wait=true} 阻塞等待执行完成；{@code triggerBy} 自定义触发者标识
     * @return 执行记录（wait=false 时为 pending，CI 需轮询 /runs/{runId}）
     * @audit 写审计日志：action=execute, entityType=ci_pipeline
     */
    @AuditLog(action = "execute", entityType = "ci_pipeline")
    @PostMapping("/{id}/trigger")
    public Result<CiPipelineRunEntity> trigger(@PathVariable String id,
                                               @RequestBody(required = false) TriggerRequest request) {
        boolean wait = request != null && Boolean.TRUE.equals(request.getWait());
        String triggerBy = request == null || request.getTriggerBy() == null || request.getTriggerBy().isBlank()
                ? UserContext.username() : request.getTriggerBy();
        return Result.ok(pipelineService.trigger(id, triggerBy, wait, TriggerType.MANUAL));
    }

    /**
     * 查询流水线的执行记录（按开始时间倒序）。
     *
     * @param id    流水线 ID
     * @param limit 返回条数上限（默认取配置 apiweb.ci.list-max-limit）
     * @return 执行记录列表
     */
    @GetMapping("/{id}/runs")
    public Result<List<CiPipelineRunEntity>> runs(@PathVariable String id,
                                                  @RequestParam(required = false) Integer limit) {
        return Result.ok(pipelineService.listRuns(id, limit));
    }

    /**
     * 查询单条执行记录详情（含执行统计与门禁判定明细）。
     *
     * @param runId 执行记录 ID
     * @return 执行记录
     */
    @GetMapping("/runs/{runId}")
    public Result<CiPipelineRunEntity> run(@PathVariable String runId) {
        return Result.ok(pipelineService.getRun(runId));
    }

    // ==================================================================
    // CI 集成
    // ==================================================================

    /**
     * 生成 / 重置 CI 触发 Token，并返回触发地址与 curl 示例。
     *
     * @param id 流水线 ID
     * @return token / url / curl（token 仅此一次返回，请妥善保管）
     * @audit 写审计日志：action=rotate_webhook, entityType=ci_pipeline
     */
    @AuditLog(action = "rotate_webhook", entityType = "ci_pipeline")
    @PostMapping("/{id}/webhook/rotate")
    public Result<Map<String, String>> rotateWebhook(@PathVariable String id) {
        return Result.ok(pipelineService.rotateWebhook(id));
    }

    /**
     * 生成可直接用于 Jenkins 的 Pipeline 脚本（含触发、轮询、门禁判定与退出码）。
     *
     * @param id 流水线 ID
     * @return {@code {content, curl}}：脚本全文与等价的 curl 触发命令
     */
    @GetMapping("/{id}/jenkinsfile")
    public Result<Map<String, String>> jenkinsfile(@PathVariable String id) {
        CiPipelineEntity pipeline = pipelineService.get(id);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("content", pipelineService.generateJenkinsfile(pipeline));
        data.put("curl", pipelineService.buildTriggerCurl(
                pipeline.getWebhookToken() == null ? "<CI_TOKEN>" : pipeline.getWebhookToken()));
        data.put("pipelineId", pipeline.getId());
        return Result.ok(data);
    }

    /** 触发请求体 */
    @Data
    public static class TriggerRequest {
        /** 是否阻塞等待执行完成（默认 false，立即返回 runId） */
        private Boolean wait;
        /** 触发者标识（页面触发时留空则用当前登录用户名） */
        private String triggerBy;
    }

    /** 构造目标选项（id / name / type） */
    private static Map<String, String> target(String id, String name, PipelineTargetType type) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("targetType", type.getCode());
        return m;
    }
}
