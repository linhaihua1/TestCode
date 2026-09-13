package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.config.CiProperties;
import com.apiweb.engine.PipelineExecMode;
import com.apiweb.engine.PipelineRunStatus;
import com.apiweb.engine.PipelineTargetType;
import com.apiweb.engine.PipelineTestType;
import com.apiweb.engine.TriggerType;
import com.apiweb.entity.CiPipelineEntity;
import com.apiweb.entity.CiPipelineRunEntity;
import com.apiweb.mapper.CiPipelineMapper;
import com.apiweb.mapper.CiPipelineRunMapper;
import com.apiweb.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * CI/CD 流水线业务服务：流水线的增删改查、触发执行、运行记录查询、Jenkinsfile 生成。
 *
 * <h3>触发链路</h3>
 * <ol>
 *   <li>校验流水线启用状态与触发频率（防 CI 重复触发）</li>
 *   <li>创建执行记录（把流水线关键配置做快照，避免流水线后续被改/删影响历史记录）</li>
 *   <li>交给 {@link CiExecutionService}：{@code wait=true} 同步阻塞，否则异步执行</li>
 *   <li>CI 侧凭 runId 轮询 {@code /api/v1/webhook/ci/runs/{runId}} 获取终态与门禁结果</li>
 * </ol>
 *
 * <h3>Jenkins 集成</h3>
 * 除「Jenkins 调用平台」外，本服务还能反向生成可直接粘贴到 Jenkins 的 Pipeline 脚本
 * （{@link #generateJenkinsfile(CiPipelineEntity)}），脚本内含触发、轮询、门禁判定与退出码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CiPipelineService {

    private final CiPipelineMapper pipelineMapper;
    private final CiPipelineRunMapper runMapper;
    private final CiExecutionService ciExecutionService;
    private final CiProperties ciProperties;

    /** webhook Token 字符集（仅小写字母与数字，便于命令行传参） */
    private static final char[] TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 查询项目的流水线列表（仅未删除，按更新时间倒序）。
     *
     * @param projectId 项目 ID
     * @return 流水线列表
     */
    public List<CiPipelineEntity> list(String projectId) {
        return pipelineMapper.selectList(
                new LambdaQueryWrapper<CiPipelineEntity>()
                        .eq(CiPipelineEntity::getProjectId, projectId)
                        .isNull(CiPipelineEntity::getDeletedAt)
                        .orderByDesc(CiPipelineEntity::getUpdatedAt));
    }

    /**
     * 查询流水线详情。
     *
     * @param id 流水线 ID
     * @return 流水线实体
     * @throws BizException 不存在时抛 notFound
     */
    public CiPipelineEntity get(String id) {
        CiPipelineEntity p = pipelineMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("流水线不存在");
        }
        return p;
    }

    /**
     * 按 webhook Token 查询流水线（免登录触发入口使用）。
     *
     * @param token webhook Token
     * @return 流水线实体
     * @throws BizException Token 无效抛 notFound；未启用 webhook 抛 forbidden
     */
    public CiPipelineEntity getByToken(String token) {
        CiPipelineEntity p = pipelineMapper.selectOne(
                new LambdaQueryWrapper<CiPipelineEntity>()
                        .eq(CiPipelineEntity::getWebhookToken, token)
                        .isNull(CiPipelineEntity::getDeletedAt));
        if (p == null) {
            throw BizException.notFound("无效的 token");
        }
        if (!Boolean.TRUE.equals(p.getWebhookEnabled())) {
            throw BizException.forbidden("该流水线未启用 CI 触发");
        }
        return p;
    }

    // ==================================================================
    // 增删改
    // ==================================================================

    /**
     * 新建流水线：补齐默认值 → 校验 → 落库。
     *
     * @param pipeline 流水线实体
     * @return 已落库的流水线
     */
    public CiPipelineEntity create(CiPipelineEntity pipeline) {
        initDefaults(pipeline);
        ciExecutionService.validate(pipeline);
        pipeline.setCreatedBy(UserContext.username());
        pipelineMapper.insert(pipeline);
        return pipeline;
    }

    /**
     * 更新流水线：未传字段回填原值（防止被默认值清空），再校验落库。
     *
     * @param id       流水线 ID
     * @param pipeline 待更新的字段
     * @return 更新后的流水线
     */
    public CiPipelineEntity update(String id, CiPipelineEntity pipeline) {
        CiPipelineEntity origin = get(id);
        mergeDefaults(pipeline, origin);
        ciExecutionService.validate(pipeline);
        pipeline.setId(id);
        pipelineMapper.updateById(pipeline);
        return pipeline;
    }

    /**
     * 软删除流水线（保留历史执行记录，便于 CI 追溯）。
     *
     * @param id 流水线 ID
     */
    public void delete(String id) {
        CiPipelineEntity p = get(id);
        p.setDeletedAt(Instant.now());
        pipelineMapper.updateById(p);
    }

    /**
     * 启用 / 停用流水线。
     *
     * @param id 流水线 ID
     * @return 更新后的流水线
     */
    public CiPipelineEntity toggle(String id) {
        CiPipelineEntity p = get(id);
        p.setEnabled(!Boolean.TRUE.equals(p.getEnabled()));
        pipelineMapper.updateById(p);
        return p;
    }

    /**
     * 生成 / 重置 CI 触发 Token（同时开启 webhook 开关，避免重置后仍无法触发）。
     *
     * @param id 流水线 ID
     * @return 含 token 与触发地址的响应
     */
    public Map<String, String> rotateWebhook(String id) {
        CiPipelineEntity p = get(id);
        String token = randomToken(32);
        p.setWebhookToken(token);
        p.setWebhookEnabled(true);
        pipelineMapper.updateById(p);
        String url = ciProperties.getPublicBaseUrl() + "/api/v1/webhook/ci/pipelines/trigger?token=" + token;
        return Map.of("token", token, "url", url,
                "curl", buildTriggerCurl(token));
    }

    // ==================================================================
    // 触发与执行记录
    // ==================================================================

    /**
     * 触发流水线执行。
     *
     * @param pipelineId 流水线 ID
     * @param triggerBy  触发者标识（用户名 / Jenkins BUILD_TAG 等）
     * @param wait       true=同步等待执行完成；false=立即返回（CI 轮询结果）
     * @param trigger    触发方式（manual / webhook / api / jenkins）
     * @return 执行记录（wait=true 时为终态，否则为 pending/running）
     * @throws BizException 流水线不存在 / 已停用 / 触发过于频繁时抛出
     */
    public CiPipelineRunEntity trigger(String pipelineId, String triggerBy, boolean wait, TriggerType trigger) {
        CiPipelineEntity pipeline = get(pipelineId);
        if (!Boolean.TRUE.equals(pipeline.getEnabled())) {
            throw BizException.badRequest("流水线已停用");
        }
        checkTriggerInterval(pipelineId);

        CiPipelineRunEntity run = new CiPipelineRunEntity();
        run.setPipelineId(pipeline.getId());
        run.setProjectId(pipeline.getProjectId());
        run.setPipelineName(pipeline.getName());
        run.setTestType(pipeline.getTestType());
        run.setTargetType(pipeline.getTargetType());
        run.setTargetId(pipeline.getTargetId());
        run.setExecMode(pipeline.getExecMode());
        run.setStatus(PipelineRunStatus.PENDING.getCode());
        run.setTriggerType(trigger.getCode());
        run.setTriggerBy(triggerBy);
        run.setJenkinsServerId(pipeline.getJenkinsServerId());
        run.setJenkinsJobName(pipeline.getJenkinsJobName());
        run.setStartedAt(Instant.now());
        run.setDuration(0);
        runMapper.insert(run);
        log.info("触发 CI 流水线: pipelineId={}, runId={}, trigger={}, by={}",
                pipelineId, run.getId(), trigger.getCode(), triggerBy);

        if (wait) {
            return ciExecutionService.execute(run.getId());
        }
        ciExecutionService.executeAsync(run.getId());
        return run;
    }

    /**
     * 查询流水线的执行记录（按开始时间倒序）。
     *
     * @param pipelineId 流水线 ID
     * @param limit      返回条数上限（默认取配置 apiweb.ci.list-max-limit）
     * @return 执行记录列表
     */
    public List<CiPipelineRunEntity> listRuns(String pipelineId, Integer limit) {
        int max = limit == null || limit <= 0 ? ciProperties.getListMaxLimit() : Math.min(limit, ciProperties.getListMaxLimit());
        return runMapper.selectList(
                new LambdaQueryWrapper<CiPipelineRunEntity>()
                        .eq(CiPipelineRunEntity::getPipelineId, pipelineId)
                        .orderByDesc(CiPipelineRunEntity::getStartedAt)
                        .last("LIMIT " + max));
    }

    /**
     * 查询单条执行记录。
     *
     * @param runId 执行记录 ID
     * @return 执行记录
     * @throws BizException 不存在时抛 notFound
     */
    public CiPipelineRunEntity getRun(String runId) {
        CiPipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw BizException.notFound("执行记录不存在");
        }
        return run;
    }

    // ==================================================================
    // Jenkins 集成产物
    // ==================================================================

    /**
     * 生成可直接粘贴到 Jenkins 的 Declarative Pipeline 脚本。
     *
     * <p>脚本流程：触发平台流水线（webhook，免登录）→ 解析 runId → 轮询执行状态
     * → 到达终态后按质量门禁结果决定构建成功/失败。
     *
     * @param pipeline 流水线实体
     * @return Jenkinsfile 脚本文本
     */
    public String generateJenkinsfile(CiPipelineEntity pipeline) {
        String baseUrl = trimSlash(ciProperties.getPublicBaseUrl());
        String token = pipeline.getWebhookToken() == null ? "<CI_TOKEN>" : pipeline.getWebhookToken();
        String typeLabel = PipelineTestType.parseOrDefault(pipeline.getTestType()).getLabel();
        return JENKINSFILE_TEMPLATE
                .replace("__PIPELINE_NAME__", nullToEmpty(pipeline.getName()))
                .replace("__PIPELINE_ID__", nullToEmpty(pipeline.getId()))
                .replace("__TEST_TYPE__", typeLabel)
                .replace("__BASE_URL__", baseUrl)
                .replace("__TOKEN__", token)
                .replace("__POLL_INTERVAL__", "10")
                .replace("__TIMEOUT_MINUTES__", "60");
    }

    /**
     * 生成 curl 触发命令（供任意 CI 系统的 Shell 步骤直接使用）。
     *
     * @param token 流水线 webhook Token
     * @return curl 命令文本
     */
    public String buildTriggerCurl(String token) {
        return "curl -s -X POST '" + trimSlash(ciProperties.getPublicBaseUrl())
                + "/api/v1/webhook/ci/pipelines/trigger?token=" + token
                + "&triggerBy=${BUILD_TAG:-ci}&wait=false'";
    }

    /** Jenkinsfile 模板：占位符在生成时替换（避免使用 String.format，减少 % 与 $ 的转义困扰） */
    private static final String JENKINSFILE_TEMPLATE = """
            // __PIPELINE_NAME__（__TEST_TYPE__）—— 由 Api-Web 测试平台自动生成
            // 流水线 ID：__PIPELINE_ID__
            //
            // 使用说明：
            //   1. 在 Jenkins 中新建「Pipeline」任务，把本文件内容粘贴到 Pipeline script
            //   2. 如平台地址/令牌与此处不一致，修改下面 environment 中的 APIWEB_BASE_URL / APIWEB_CI_TOKEN
            //   3. 质量门禁未通过时，本 Pipeline 会以非零退出码结束，构建标记为 FAILURE
            //
            // 依赖插件：Pipeline: Utility Steps（提供 readJSON）。若未安装，可改用
            // sh + grep/sed 从响应里解析 runId 与 status。
            pipeline {
                agent any

                environment {
                    APIWEB_BASE_URL = '__BASE_URL__'
                    APIWEB_CI_TOKEN = '__TOKEN__'
                    RUN_ID = ''
                }

                options {
                    timestamps()
                    timeout(time: __TIMEOUT_MINUTES__, unit: 'MINUTES')
                }

                stages {
                    stage('触发测试') {
                        steps {
                            script {
                                echo '触发 Api-Web 流水线：__PIPELINE_NAME__'
                                def resp = sh(
                                    script: "curl -s -X POST '${APIWEB_BASE_URL}/api/v1/webhook/ci/pipelines/trigger?token=${APIWEB_CI_TOKEN}&triggerBy=${BUILD_TAG}&wait=false'",
                                    returnStdout: true
                                ).trim()
                                echo "触发响应: ${resp}"
                                def json = readJSON text: resp
                                if (json.code != 0) {
                                    error "触发失败: ${json.message}"
                                }
                                RUN_ID = json.data.id
                                echo "执行记录 ID: ${RUN_ID}"
                            }
                        }
                    }

                    stage('等待执行结果') {
                        steps {
                            script {
                                def status = ''
                                def gatePassed = true
                                def summary = ''
                                timeout(time: __TIMEOUT_MINUTES__, unit: 'MINUTES') {
                                    waitUntil {
                                        sleep __POLL_INTERVAL__
                                        def resp = sh(
                                            script: "curl -s '${APIWEB_BASE_URL}/api/v1/webhook/ci/runs/${RUN_ID}?token=${APIWEB_CI_TOKEN}'",
                                            returnStdout: true
                                        ).trim()
                                        def json = readJSON text: resp
                                        if (json.code != 0) {
                                            error "查询执行结果失败: ${json.message}"
                                        }
                                        status = json.data.status
                                        gatePassed = (json.data.gatePassed == null ? true : json.data.gatePassed == 1)
                                        summary = json.data.summary
                                        echo "当前状态: ${status}"
                                        return (status == 'success' || status == 'failed' || status == 'error')
                                    }
                                }
                                echo "执行统计: ${summary}"

                                if (status != 'success') {
                                    error "流水线执行未通过: status=${status}"
                                }
                                if (!gatePassed) {
                                    error "质量门禁未通过，流水线判定失败"
                                }
                                echo '流水线执行通过，质量门禁已满足'
                            }
                        }
                    }
                }

                post {
                    always {
                        echo "执行记录: ${APIWEB_BASE_URL}/#/ci/pipelines (执行 ID: ${RUN_ID})"
                    }
                }
            }
            """;

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 触发频率限制：同一流水线在配置间隔内只允许触发一次，防止 CI 重复触发把执行机打满。
     */
    private void checkTriggerInterval(String pipelineId) {
        int seconds = ciProperties.getTriggerMinIntervalSeconds();
        if (seconds <= 0) return;
        List<CiPipelineRunEntity> recent = runMapper.selectList(
                new LambdaQueryWrapper<CiPipelineRunEntity>()
                        .eq(CiPipelineRunEntity::getPipelineId, pipelineId)
                        .orderByDesc(CiPipelineRunEntity::getStartedAt)
                        .last("LIMIT 1"));
        if (recent.isEmpty()) return;
        Instant lastStart = recent.get(0).getStartedAt();
        if (lastStart != null && lastStart.isAfter(Instant.now().minusSeconds(seconds))) {
            throw BizException.badRequest("流水线触发过于频繁，请 " + seconds + " 秒后再试");
        }
    }

    /** 新建时补齐默认配置 */
    private void initDefaults(CiPipelineEntity p) {
        if (p.getTestType() == null) p.setTestType(PipelineTestType.API.getCode());
        if (p.getTargetType() == null) {
            p.setTargetType(PipelineTargetType.defaultOf(PipelineTestType.parse(p.getTestType())).getCode());
        }
        if (p.getExecMode() == null) p.setExecMode(PipelineExecMode.LOCAL.getCode());
        if (p.getEnabled() == null) p.setEnabled(true);
        if (p.getWebhookEnabled() == null) p.setWebhookEnabled(false);
        if (p.getQualityGate() == null) p.setQualityGate("[]");
        if (p.getNotifyChannels() == null) p.setNotifyChannels("[]");
        if (p.getJenkinsParams() == null) p.setJenkinsParams("{}");
    }

    /** 更新时回填原值，避免部分字段更新被默认值覆盖 */
    private void mergeDefaults(CiPipelineEntity p, CiPipelineEntity origin) {
        if (p.getProjectId() == null) p.setProjectId(origin.getProjectId());
        if (p.getName() == null) p.setName(origin.getName());
        if (p.getDescription() == null) p.setDescription(origin.getDescription());
        if (p.getTestType() == null) p.setTestType(origin.getTestType());
        if (p.getTargetType() == null) p.setTargetType(origin.getTargetType());
        if (p.getTargetId() == null) p.setTargetId(origin.getTargetId());
        if (p.getExecMode() == null) p.setExecMode(origin.getExecMode());
        if (p.getJenkinsServerId() == null) p.setJenkinsServerId(origin.getJenkinsServerId());
        if (p.getJenkinsJobName() == null) p.setJenkinsJobName(origin.getJenkinsJobName());
        if (p.getJenkinsParams() == null) p.setJenkinsParams(origin.getJenkinsParams());
        if (p.getQualityGate() == null) p.setQualityGate(origin.getQualityGate());
        if (p.getNotifyChannels() == null) p.setNotifyChannels(origin.getNotifyChannels());
        if (p.getEnabled() == null) p.setEnabled(origin.getEnabled());
        if (p.getWebhookEnabled() == null) p.setWebhookEnabled(origin.getWebhookEnabled());
        if (p.getWebhookToken() == null) p.setWebhookToken(origin.getWebhookToken());
    }

    private static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(TOKEN_ALPHABET[RANDOM.nextInt(TOKEN_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trimSlash(String s) {
        if (s == null) return "";
        String v = s.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }
}
