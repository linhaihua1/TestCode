package com.apiweb.service;

import com.apiweb.engine.EngineDtos;
import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.mapper.ReportDetailMapper;
import com.apiweb.mapper.ReportMapper;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 报告生成服务：把任务/场景的执行结果（runs）汇总为一条 {@link ReportEntity} 及其明细。
 *
 * <p>任务执行（本地 {@code TaskExecutorService} 与 MQ {@code ApiTaskConsumer}）此前只写
 * {@code t_test_task_run}，导致「测试报告页」无数据。本服务统一负责在执行完成后
 * 生成 {@code t_report} + {@code t_report_detail}，并回填统计字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ReportMapper reportMapper;
    private final ReportDetailMapper reportDetailMapper;

    /**
     * 从一组任务 run 记录汇总生成一条接口测试报告。
     *
     * @param projectId   项目 ID（来自任务）
     * @param taskId      关联任务 ID（可为 null）
     * @param name        报告名称（任务名）
     * @param triggerType 触发方式 manual/schedule/webhook/api
     * @param triggerBy   触发者用户名
     * @param runNameMap  runId -> 用例名（用于明细展示）
     * @param runs        该任务本轮全部 run（含每个用例的执行结果）
     * @return 生成的报告（已落库）
     */
    public ReportEntity generateFromRuns(String projectId, String taskId, String name,
                                         String triggerType, String triggerBy,
                                         java.util.Map<String, String> runNameMap,
                                         List<TestTaskRunEntity> runs) {
        ReportEntity report = new ReportEntity();
        report.setProjectId(projectId);
        report.setTaskId(taskId);
        report.setName(name);
        report.setTriggerType(triggerType);
        report.setTriggerBy(triggerBy);

        int total = runs.size();
        int passed = 0;
        int failed = 0;
        int error = 0;
        int skipped = 0;
        int totalAssertions = 0;
        int passedAssertions = 0;
        int failedAssertions = 0;
        long sumDuration = 0;
        long maxResponse = 0;

        Instant startedAt = null;
        Instant finishedAt = null;

        for (TestTaskRunEntity run : runs) {
            String result = run.getResult();
            if ("success".equals(result)) {
                passed++;
            } else if ("failed".equals(result)) {
                failed++;
            } else if ("error".equals(result)) {
                error++;
            } else {
                // pending / running / skipped / null 均视为未通过（跳过）
                skipped++;
            }

            if (run.getStartedAt() != null
                    && (startedAt == null || run.getStartedAt().isBefore(startedAt))) {
                startedAt = run.getStartedAt();
            }
            if (run.getEndedAt() != null
                    && (finishedAt == null || run.getEndedAt().isAfter(finishedAt))) {
                finishedAt = run.getEndedAt();
            }
            if (run.getDuration() != null) {
                sumDuration += run.getDuration();
                maxResponse = Math.max(maxResponse, run.getDuration());
            }

            // 统计断言：从 run.details 里的 steps[].assertions[] 聚合
            EngineDtos.ExecutionResult er = parseResult(run.getDetails());
            for (EngineDtos.StepResult step : er.getSteps()) {
                if (step.getAssertions() == null) continue;
                for (EngineDtos.AssertionResult a : step.getAssertions()) {
                    totalAssertions++;
                    if (a.isPassed()) {
                        passedAssertions++;
                    } else {
                        failedAssertions++;
                    }
                }
            }
        }

        report.setStartedAt(startedAt != null ? startedAt : Instant.now());
        report.setFinishedAt(finishedAt);
        report.setTotalCases(total);
        report.setPassedCases(passed);
        report.setFailedCases(failed);
        report.setErrorCases(error);
        report.setSkippedCases(skipped);
        report.setTotalAssertions(totalAssertions);
        report.setPassedAssertions(passedAssertions);
        report.setFailedAssertions(failedAssertions);
        report.setDuration((int) sumDuration);
        // avgResponseTime 用平均单用例耗时近似（语义为平均响应，任务场景下近似为平均用例耗时）
        report.setAvgResponseTime(total == 0 ? 0 : (int) (sumDuration / total));
        report.setStatus(resolveStatus(error, failed, passed, skipped, total));

        reportMapper.insert(report);

        // 写明细：每个 run 一条
        for (TestTaskRunEntity run : runs) {
            ReportDetailEntity detail = new ReportDetailEntity();
            detail.setReportId(report.getId());
            detail.setStepName(runNameMap != null ? runNameMap.getOrDefault(run.getId(), run.getId())
                    : run.getId());
            detail.setStatus(normalizeStatus(run.getResult()));
            EngineDtos.ExecutionResult er = parseResult(run.getDetails());
            if (er.getErrorLog() != null && !er.getErrorLog().isBlank()) {
                detail.setError(er.getErrorLog());
            } else if ("error".equals(run.getResult()) || "failed".equals(run.getResult())) {
                detail.setError(extractFirstError(er));
            }
            detail.setAssertions(JsonUtils.toJson(collectAssertions(er)));
            detail.setExtracts(JsonUtils.toJson(collectExtracts(er)));
            reportDetailMapper.insert(detail);
        }

        log.info("已生成接口测试报告: id={}, name={}, total={}, passed={}, failed={}, error={}",
                report.getId(), name, total, passed, failed, error);
        return report;
    }

    private String resolveStatus(int error, int failed, int passed, int skipped, int total) {
        if (total == 0) return "passed";
        if (error > 0) return "error";
        if (failed > 0) return "failed";
        return "passed";
    }

    private String normalizeStatus(String result) {
        if (result == null) return "skipped";
        return switch (result) {
            case "success" -> "success";
            case "failed" -> "failed";
            case "error" -> "error";
            default -> "skipped";
        };
    }

    private String extractFirstError(EngineDtos.ExecutionResult er) {
        for (EngineDtos.StepResult step : er.getSteps()) {
            if (step.getError() != null && !step.getError().isBlank()) {
                return step.getError();
            }
        }
        return null;
    }

    private EngineDtos.ExecutionResult parseResult(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank() || "[]".equals(detailsJson.trim())) {
            return EngineDtos.ExecutionResult.builder().result("skipped").build();
        }
        try {
            return JsonUtils.fromJson(detailsJson, EngineDtos.ExecutionResult.class);
        } catch (Exception e) {
            return EngineDtos.ExecutionResult.builder().result("error").build();
        }
    }

    private List<EngineDtos.AssertionResult> collectAssertions(EngineDtos.ExecutionResult er) {
        List<EngineDtos.AssertionResult> list = new ArrayList<>();
        for (EngineDtos.StepResult step : er.getSteps()) {
            if (step.getAssertions() != null) list.addAll(step.getAssertions());
        }
        return list;
    }

    private List<EngineDtos.ExtractResult> collectExtracts(EngineDtos.ExecutionResult er) {
        List<EngineDtos.ExtractResult> list = new ArrayList<>();
        for (EngineDtos.StepResult step : er.getSteps()) {
            if (step.getExtracts() != null) list.addAll(step.getExtracts());
        }
        return list;
    }
}
