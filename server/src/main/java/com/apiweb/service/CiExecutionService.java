package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.config.CiProperties;
import com.apiweb.engine.JenkinsBuildResult;
import com.apiweb.engine.PipelineExecMode;
import com.apiweb.engine.PipelineRunStatus;
import com.apiweb.engine.PipelineTargetType;
import com.apiweb.engine.PipelineTestType;
import com.apiweb.engine.QualityGateMetric;
import com.apiweb.engine.QualityGateOperator;
import com.apiweb.engine.RunStatus;
import com.apiweb.engine.TaskExecutorService;
import com.apiweb.entity.CiJenkinsServerEntity;
import com.apiweb.entity.CiPipelineEntity;
import com.apiweb.entity.CiPipelineRunEntity;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.PerfReportEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.entity.UiReportEntity;
import com.apiweb.entity.UiScenarioEntity;
import com.apiweb.entity.UiScenarioStepEntity;
import com.apiweb.entity.UiTestCaseEntity;
import com.apiweb.mapper.CiJenkinsServerMapper;
import com.apiweb.mapper.CiPipelineMapper;
import com.apiweb.mapper.CiPipelineRunMapper;
import com.apiweb.mapper.PerfCaseMapper;
import com.apiweb.mapper.PerfReportMapper;
import com.apiweb.mapper.ReportMapper;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.UiReportMapper;
import com.apiweb.mapper.UiScenarioMapper;
import com.apiweb.mapper.UiScenarioStepMapper;
import com.apiweb.mapper.UiTestCaseMapper;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CI/CD 流水线执行器：真正跑用例的地方。
 *
 * <h3>执行方式</h3>
 * <ul>
 *   <li>{@code local}：按测试类型分别调用接口执行引擎（{@link TaskExecutorService}）、
 *       UI 执行服务（{@link UiExecutionService}）、性能执行服务（{@link PerfExecutionService}），
 *       执行完汇总统计并做质量门禁判定</li>
 *   <li>{@code jenkins}：调用 {@link JenkinsService} 触发远端 Job，随后解析构建号并轮询
 *       构建结果，把 Jenkins 的 SUCCESS/FAILURE/UNSTABLE/ABORTED 映射为流水线状态</li>
 * </ul>
 *
 * <h3>异步与同步</h3>
 * {@link #executeAsync(String)} 供接口快速返回（CI 触发后立即拿到 runId 再轮询）；
 * {@link #execute(String)} 同步阻塞，供 {@code wait=true} 的触发方式使用。
 * 注意：{@code @Async} 必须跨 Bean 调用才生效，因此本类独立成 Service，由
 * {@code CiPipelineService} 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CiExecutionService {

    private final CiPipelineMapper pipelineMapper;
    private final CiPipelineRunMapper runMapper;
    private final CiJenkinsServerMapper jenkinsServerMapper;
    private final CiProperties ciProperties;

    private final TestTaskMapper testTaskMapper;
    private final TaskExecutorService taskExecutorService;
    private final ReportMapper reportMapper;

    private final UiScenarioMapper uiScenarioMapper;
    private final UiScenarioStepMapper uiScenarioStepMapper;
    private final UiTestCaseMapper uiTestCaseMapper;
    private final UiReportMapper uiReportMapper;
    private final UiExecutionService uiExecutionService;

    private final PerfCaseMapper perfCaseMapper;
    private final PerfReportMapper perfReportMapper;
    private final PerfExecutionService perfExecutionService;

    private final JenkinsService jenkinsService;

    /** summary 中的键：总用例数 */
    private static final String K_TOTAL = "total";
    /** summary 中的键：通过数 */
    private static final String K_PASSED = "passed";
    /** summary 中的键：失败数（断言失败） */
    private static final String K_FAILED = "failed";
    /** summary 中的键：错误数（执行异常） */
    private static final String K_ERROR = "error";
    /** summary 中的键：错误消息 */
    private static final String K_MESSAGE = "message";

    // ==================================================================
    // 入口
    // ==================================================================

    /**
     * 异步执行流水线（CI 触发默认走这里，接口立即返回 runId）。
     *
     * @param runId 流水线执行记录 ID
     */
    @Async
    public void executeAsync(String runId) {
        execute(runId);
    }

    /**
     * 同步执行一条流水线执行记录：跑用例 → 汇总统计 → 质量门禁判定 → 落库终态。
     *
     * <p>副作用：多次更新 {@code t_ci_pipeline_run}（状态、统计、门禁、日志、耗时），
     * 并在本地执行时产生对应的测试报告（t_report / t_ui_report / t_perf_report）。
     *
     * @param runId 流水线执行记录 ID
     * @return 执行完成后的记录（终态）；记录不存在返回 null
     */
    public CiPipelineRunEntity execute(String runId) {
        CiPipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            log.warn("流水线执行记录不存在: runId={}", runId);
            return null;
        }
        // 流水线被删除时仍可用 run 上的快照字段继续判定，不阻断历史记录展示
        CiPipelineEntity pipeline = pipelineMapper.selectById(run.getPipelineId());

        long start = System.currentTimeMillis();
        run.setStatus(PipelineRunStatus.RUNNING.getCode());
        run.setStartedAt(Instant.now());
        runMapper.updateById(run);

        try {
            if (PipelineExecMode.parseOrDefault(run.getExecMode()) == PipelineExecMode.JENKINS) {
                executeJenkins(run, pipeline);
            } else {
                executeLocal(run, pipeline);
            }
        } catch (BizException e) {
            fail(run, PipelineRunStatus.ERROR.getCode(), e.getMessage(), start);
        } catch (Exception e) {
            log.error("CI 流水线执行异常: runId={}", runId, e);
            fail(run, PipelineRunStatus.ERROR.getCode(), "执行异常：" + e.getMessage(), start);
        }
        return run;
    }

    // ==================================================================
    // 本地执行
    // ==================================================================

    /**
     * 平台本地执行三类测试，并做质量门禁判定。
     */
    private void executeLocal(CiPipelineRunEntity run, CiPipelineEntity pipeline) {
        long start = System.currentTimeMillis();
        PipelineTargetType targetType = PipelineTargetType.parseOrDefault(run.getTargetType());
        Map<String, Object> summary = switch (targetType) {
            case TEST_TASK -> executeApiTask(run);
            case UI_SCENARIO -> executeUiScenario(run);
            case UI_TEST -> executeUiCase(run);
            case PERF_CASE -> executePerfCase(run);
        };

        List<Map<String, Object>> gateResult = evaluateGate(pipeline, summary);
        boolean gatePassed = gateResult.stream().allMatch(g -> Boolean.TRUE.equals(g.get("passed")));

        // 终态：执行异常 → ERROR；门禁未通过 → FAILED；否则 SUCCESS
        String status;
        if (summary.containsKey(K_MESSAGE)) {
            status = PipelineRunStatus.ERROR.getCode();
        } else if (!gatePassed || count(summary, K_FAILED) + count(summary, K_ERROR) > 0) {
            status = PipelineRunStatus.FAILED.getCode();
        } else {
            status = PipelineRunStatus.SUCCESS.getCode();
        }

        run.setSummary(JsonUtils.toJson(summary));
        run.setGateResult(JsonUtils.toJson(gateResult));
        run.setGatePassed(gatePassed ? 1 : 0);
        run.setStatus(status);
        run.setDuration((int) (System.currentTimeMillis() - start));
        run.setEndedAt(Instant.now());
        runMapper.updateById(run);
        log.info("CI 流水线本地执行完成: runId={}, status={}, gatePassed={}", run.getId(), status, gatePassed);
    }

    /**
     * 执行接口自动化：调用任务执行引擎跑测试任务，并从最新报告中取响应时间指标。
     */
    private Map<String, Object> executeApiTask(CiPipelineRunEntity run) {
        TestTaskEntity task = testTaskMapper.selectById(run.getTargetId());
        if (task == null) {
            return errorSummary("测试任务不存在（可能已被删除）");
        }
        List<TestTaskRunEntity> runs = taskExecutorService.execute(task);
        List<String> reportIds = new ArrayList<>();
        int passed = 0, failed = 0, error = 0, other = 0;
        for (TestTaskRunEntity r : runs) {
            reportIds.add(r.getId());
            String result = r.getResult();
            if (RunStatus.SUCCESS.getCode().equals(result)) passed++;
            else if (RunStatus.FAILED.getCode().equals(result)) failed++;
            else if (RunStatus.ERROR.getCode().equals(result)) error++;
            else other++;
        }
        Map<String, Object> summary = baseSummary(passed, failed, error, other);
        // 接口任务的响应时间指标来自执行后生成的报告（t_report），取最近一条
        ReportEntity report = reportMapper.selectOne(
                new LambdaQueryWrapper<ReportEntity>()
                        .eq(ReportEntity::getTaskId, task.getId())
                        .orderByDesc(ReportEntity::getStartedAt)
                        .last("LIMIT 1"));
        if (report != null) {
            put(summary, QualityGateMetric.AVG_RESPONSE_TIME.getCode(), nvl(report.getAvgResponseTime()));
            put(summary, QualityGateMetric.P95_RESPONSE_TIME.getCode(), nvl(report.getP95ResponseTime()));
            // 报告 ID 也纳入 reportIds，便于从流水线记录跳转报告详情
            reportIds.add(report.getId());
        }
        run.setReportIds(JsonUtils.toJson(reportIds));
        return summary;
    }

    /**
     * 执行 UI 场景：按步骤顺序同步执行其下的每个 UI 用例。
     */
    private Map<String, Object> executeUiScenario(CiPipelineRunEntity run) {
        UiScenarioEntity scenario = uiScenarioMapper.selectById(run.getTargetId());
        if (scenario == null) {
            return errorSummary("UI 场景不存在（可能已被删除）");
        }
        List<UiScenarioStepEntity> steps = uiScenarioStepMapper.selectList(
                new LambdaQueryWrapper<UiScenarioStepEntity>()
                        .eq(UiScenarioStepEntity::getScenarioId, scenario.getId())
                        .orderByAsc(UiScenarioStepEntity::getSortOrder));
        List<String> reportIds = new ArrayList<>();
        int passed = 0, failed = 0, error = 0, other = 0;
        for (UiScenarioStepEntity step : steps) {
            UiTestCaseEntity testCase = step.getUiTestCaseId() == null ? null
                    : uiTestCaseMapper.selectById(step.getUiTestCaseId());
            if (testCase == null) continue;
            UiReportEntity report = newUiReport(scenario.getProjectId(), testCase.getId(),
                    scenario.getName() + " / " + testCase.getName());
            uiExecutionService.execute(report.getId(), testCase.getId());
            UiReportEntity finished = uiReportMapper.selectById(report.getId());
            reportIds.add(report.getId());
            String status = finished == null ? RunStatus.ERROR.getCode() : finished.getStatus();
            if (RunStatus.SUCCESS.getCode().equals(status)) passed++;
            else if (RunStatus.FAILED.getCode().equals(status)) failed++;
            else if (RunStatus.ERROR.getCode().equals(status)) error++;
            else other++;
        }
        run.setReportIds(JsonUtils.toJson(reportIds));
        return baseSummary(passed, failed, error, other);
    }

    /**
     * 执行单个 UI 用例。
     */
    private Map<String, Object> executeUiCase(CiPipelineRunEntity run) {
        UiTestCaseEntity testCase = uiTestCaseMapper.selectById(run.getTargetId());
        if (testCase == null) {
            return errorSummary("UI 用例不存在（可能已被删除）");
        }
        UiReportEntity report = newUiReport(testCase.getProjectId(), testCase.getId(), testCase.getName());
        uiExecutionService.execute(report.getId(), testCase.getId());
        UiReportEntity finished = uiReportMapper.selectById(report.getId());
        String status = finished == null ? RunStatus.ERROR.getCode() : finished.getStatus();
        boolean ok = RunStatus.SUCCESS.getCode().equals(status);
        boolean err = RunStatus.ERROR.getCode().equals(status);
        run.setReportIds(JsonUtils.toJson(List.of(report.getId())));
        return baseSummary(ok ? 1 : 0, ok || err ? 0 : 1, err ? 1 : 0, 0);
    }

    /**
     * 执行性能用例：创建报告后交给内嵌 JMeter 同步执行，再把 JMeter 结果折算为门禁指标。
     */
    private Map<String, Object> executePerfCase(CiPipelineRunEntity run) {
        PerfCaseEntity perfCase = perfCaseMapper.selectById(run.getTargetId());
        if (perfCase == null) {
            return errorSummary("性能用例不存在（可能已被删除）");
        }
        PerfReportEntity report = new PerfReportEntity();
        report.setProjectId(perfCase.getProjectId());
        report.setCaseId(perfCase.getId());
        report.setName(perfCase.getName());
        report.setStatus(RunStatus.PENDING.getCode());
        report.setStartedAt(Instant.now());
        report.setSummary("{}");
        report.setSeries("[]");
        report.setLabels("[]");
        report.setErrors("[]");
        perfReportMapper.insert(report);

        perfExecutionService.execute(report.getId(), perfCase.getId());
        PerfReportEntity finished = perfReportMapper.selectById(report.getId());
        run.setReportIds(JsonUtils.toJson(List.of(report.getId())));

        Map<String, Object> perfSummary = parseJsonMap(finished == null ? null : finished.getSummary());
        int sampleCount = toInt(perfSummary.get("sampleCount"));
        int errorCount = toInt(perfSummary.get("errorCount"));
        double avg = toDouble(perfSummary.get("avg"));
        double p95 = toDouble(perfSummary.get("p95"));
        double tps = toDouble(perfSummary.get("tps"));

        Map<String, Object> summary = baseSummary(
                sampleCount - errorCount, errorCount, 0, 0);
        put(summary, QualityGateMetric.ERROR_COUNT.getCode(), errorCount);
        put(summary, QualityGateMetric.ERROR_RATE.getCode(), rate(errorCount, sampleCount));
        put(summary, QualityGateMetric.AVG_RESPONSE_TIME.getCode(), avg);
        put(summary, QualityGateMetric.P95_RESPONSE_TIME.getCode(), p95);
        put(summary, QualityGateMetric.TPS.getCode(), tps);
        // 执行引擎异常时把 message 透出，终态判为 ERROR
        if (finished != null && finished.getMessage() != null && !finished.getMessage().isBlank()) {
            summary.put(K_MESSAGE, finished.getMessage());
        }
        return summary;
    }

    // ==================================================================
    // Jenkins 执行
    // ==================================================================

    /**
     * 调用 Jenkins 触发远端 Job，并轮询构建结果回写执行记录。
     *
     * <p>为什么轮询而不是回调：Jenkins 回调需要额外配置 webhook 插件与外网可达地址，
     * 内网部署场景往往不具备条件；轮询实现更简单可靠，且由配置控制超时。
     */
    private void executeJenkins(CiPipelineRunEntity run, CiPipelineEntity pipeline) {
        long start = System.currentTimeMillis();
        CiJenkinsServerEntity server = jenkinsServerMapper.selectById(run.getJenkinsServerId());
        if (server == null) {
            fail(run, PipelineRunStatus.ERROR.getCode(), "Jenkins 服务器不存在（可能已被删除）", start);
            return;
        }
        Map<String, String> params = new LinkedHashMap<>();
        Map<String, Object> rawParams = parseJsonMap(pipeline == null ? null : pipeline.getJenkinsParams());
        rawParams.forEach((k, v) -> {
            if (k != null && v != null) params.put(k, String.valueOf(v));
        });
        // 把平台侧上下文注入构建参数，便于 Jenkins Job 里回调平台或记录来源
        params.putIfAbsent("APIWEB_PIPELINE_ID", run.getPipelineId());
        params.putIfAbsent("APIWEB_RUN_ID", run.getId());

        JenkinsService.TriggerResult triggered =
                jenkinsService.trigger(server, run.getJenkinsJobName(), params);
        if (!triggered.isSuccess()) {
            fail(run, PipelineRunStatus.ERROR.getCode(), triggered.getMessage(), start);
            return;
        }
        run.setJenkinsQueueUrl(triggered.getQueueUrl());
        runMapper.updateById(run);

        Integer buildNo = jenkinsService.resolveBuildNumber(server, triggered.getQueueUrl());
        if (buildNo == null) {
            fail(run, PipelineRunStatus.ERROR.getCode(), "未能解析 Jenkins 构建号（任务可能仍在排队或已被取消）", start);
            return;
        }
        run.setJenkinsBuildNo(buildNo);
        run.setJenkinsBuildUrl(String.format("%s/job/%s/%d", trimSlash(server.getBaseUrl()),
                run.getJenkinsJobName(), buildNo));
        runMapper.updateById(run);

        // 轮询构建直到终态或超时
        long deadline = System.currentTimeMillis()
                + Math.max(1, ciProperties.getJenkins().getBuildPollTimeoutMinutes()) * 60_000L;
        JenkinsBuildResult result = JenkinsBuildResult.RUNNING;
        while (System.currentTimeMillis() < deadline) {
            JenkinsService.BuildInfo info =
                    jenkinsService.getBuild(server, run.getJenkinsJobName(), buildNo);
            if (!info.isExists()) {
                result = JenkinsBuildResult.NOT_FOUND;
                break;
            }
            result = info.getResult();
            if (result.isFinished()) {
                if (info.getUrl() != null && !info.getUrl().isBlank()) {
                    run.setJenkinsBuildUrl(info.getUrl());
                }
                break;
            }
            try {
                Thread.sleep(Math.max(500, ciProperties.getJenkins().getBuildPollIntervalMs()));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!result.isFinished()) {
            fail(run, PipelineRunStatus.ERROR.getCode(),
                    "等待 Jenkins 构建结束超时（超过 " + ciProperties.getJenkins().getBuildPollTimeoutMinutes() + " 分钟）", start);
            return;
        }

        run.setJenkinsResult(result.getCode());
        String console = jenkinsService.consoleText(server, run.getJenkinsJobName(), buildNo);
        if (console != null && !console.isBlank()) {
            run.setLog(console);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("jenkinsBuildNo", buildNo);
        summary.put("jenkinsResult", result.getCode());
        summary.put("jenkinsJob", run.getJenkinsJobName());
        run.setSummary(JsonUtils.toJson(summary));
        run.setStatus(result.toRunStatus().getCode());
        run.setGatePassed(result == JenkinsBuildResult.SUCCESS ? 1 : 0);
        run.setGateResult(JsonUtils.toJson(List.of()));
        run.setDuration((int) (System.currentTimeMillis() - start));
        run.setEndedAt(Instant.now());
        runMapper.updateById(run);
        log.info("CI 流水线 Jenkins 执行结束: runId={}, buildNo={}, result={}", run.getId(), buildNo, result.getCode());
    }

    // ==================================================================
    // 质量门禁
    // ==================================================================

    /**
     * 按流水线配置的门禁规则判定是否通过。
     *
     * <p>规则语义：<b>指标值 操作符 阈值 成立即违规</b>。
     * 没有任何规则时返回空列表（视为通过，由用例失败数决定最终状态）。
     *
     * @param pipeline 流水线（提供 qualityGate 配置）
     * @param summary  本次执行统计
     * @return 判定明细：[{metric, metricLabel, operator, threshold, actual, passed}]
     */
    public static List<Map<String, Object>> evaluateGate(CiPipelineEntity pipeline, Map<String, Object> summary) {
        List<Map<String, Object>> rules = parseJsonList(pipeline == null ? null : pipeline.getQualityGate());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            QualityGateMetric metric = QualityGateMetric.parse(str(rule.get("metric")));
            if (metric == null) continue;
            QualityGateOperator operator = QualityGateOperator.parseOrDefault(str(rule.get("operator")));
            double threshold = toDouble(rule.get("threshold"));
            double actual = toDouble(summary.get(metric.getCode()));
            boolean violated = operator.violate(actual, threshold);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("metric", metric.getCode());
            item.put("metricLabel", metric.getLabelWithUnit());
            item.put("operator", operator.getCode());
            item.put("operatorSymbol", operator.getSymbol());
            item.put("threshold", threshold);
            item.put("actual", actual);
            item.put("passed", !violated);
            result.add(item);
        }
        return result;
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    /**
     * 组装基础统计（总数 / 通过 / 失败 / 错误 / 通过率 / 失败率 / 错误率）。
     */
    private Map<String, Object> baseSummary(int passed, int failed, int error, int other) {
        int total = passed + failed + error + other;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put(K_TOTAL, total);
        summary.put(K_PASSED, passed);
        summary.put(K_FAILED, failed);
        summary.put(K_ERROR, error);
        put(summary, QualityGateMetric.PASS_RATE.getCode(), rate(passed, total));
        put(summary, QualityGateMetric.FAIL_RATE.getCode(), rate(failed, total));
        put(summary, QualityGateMetric.ERROR_RATE.getCode(), rate(error, total));
        return summary;
    }

    /** 目标不存在等场景的错误统计（终态会被判为 ERROR） */
    private Map<String, Object> errorSummary(String message) {
        Map<String, Object> summary = baseSummary(0, 0, 0, 0);
        summary.put(K_MESSAGE, message);
        return summary;
    }

    /** 执行过程异常：直接落终态并写日志 */
    private void fail(CiPipelineRunEntity run, String status, String message, long start) {
        run.setStatus(status);
        run.setLog(message);
        run.setDuration((int) (System.currentTimeMillis() - start));
        run.setEndedAt(Instant.now());
        runMapper.updateById(run);
    }

    /** 创建一条 PENDING 的 UI 报告 */
    private UiReportEntity newUiReport(String projectId, String caseId, String name) {
        UiReportEntity report = new UiReportEntity();
        report.setProjectId(projectId);
        report.setTestCaseId(caseId);
        report.setName(name);
        report.setStatus(RunStatus.PENDING.getCode());
        report.setStartedAt(Instant.now());
        report.setDetails("[]");
        uiReportMapper.insert(report);
        return report;
    }

    private static void put(Map<String, Object> map, String key, double value) {
        map.put(key, Math.round(value * 100.0) / 100.0);
    }

    private static int count(Map<String, Object> summary, String key) {
        return toInt(summary.get(key));
    }

    private static double rate(int part, int total) {
        return total <= 0 ? 0.0 : (part * 100.0 / total);
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return 0;
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String trimSlash(String s) {
        if (s == null) return "";
        String v = s.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object o = JsonUtils.fromJson(json, Map.class);
            return o instanceof Map ? (Map<String, Object>) o : Map.of();
        } catch (Exception e) {
            log.warn("解析 JSON 对象失败: {}", json, e);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            Object o = JsonUtils.fromJson(json, List.class);
            return o instanceof List ? (List<Map<String, Object>>) o : List.of();
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}", json, e);
            return List.of();
        }
    }

    /**
     * 校验流水线配置的合法性（创建/更新时调用）。
     *
     * @param pipeline 流水线实体
     * @throws BizException 配置不合法时抛出
     */
    public void validate(CiPipelineEntity pipeline) {
        if (pipeline.getName() == null || pipeline.getName().isBlank()) {
            throw BizException.badRequest("流水线名称必填");
        }
        PipelineTestType testType = PipelineTestType.parse(pipeline.getTestType());
        if (testType == null) {
            throw BizException.badRequest("testType 必须是 api / ui / perf");
        }
        PipelineExecMode execMode = PipelineExecMode.parseOrDefault(pipeline.getExecMode());
        pipeline.setExecMode(execMode.getCode());
        if (execMode == PipelineExecMode.JENKINS) {
            if (pipeline.getJenkinsServerId() == null || pipeline.getJenkinsServerId().isBlank()) {
                throw BizException.badRequest("Jenkins 执行方式必须选择 Jenkins 服务器");
            }
            if (pipeline.getJenkinsJobName() == null || pipeline.getJenkinsJobName().isBlank()) {
                throw BizException.badRequest("Jenkins Job 名称必填");
            }
        }
        if (pipeline.getTargetId() == null || pipeline.getTargetId().isBlank()) {
            throw BizException.badRequest("执行目标必填");
        }
        PipelineTargetType targetType = PipelineTargetType.parse(pipeline.getTargetType());
        if (targetType == null) {
            // 未显式指定时按测试类型推导默认目标类型
            targetType = PipelineTargetType.defaultOf(testType);
            pipeline.setTargetType(targetType.getCode());
        }
    }
}
