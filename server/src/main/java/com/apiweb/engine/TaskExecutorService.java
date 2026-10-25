package com.apiweb.engine;

import com.apiweb.common.BizException;
import com.apiweb.config.EngineProperties;
import com.apiweb.engine.variable.VariableMerger;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.TestTaskRunMapper;
import com.apiweb.service.ExecutionSupportService;
import com.apiweb.service.ReportGenerationService;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务本地执行器（无需 RabbitMQ,单进程内执行）。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>并行池</b>：基于 ThreadPoolExecutor,支持动态配置池大小（1-200）</li>
 *   <li><b>失败策略</b>：
 *     <ul>
 *       <li>{@code stop_on_fail}：任一用例失败即停止后续用例</li>
 *       <li>{@code continue_all}：所有用例跑完,失败不中断</li>
 *       <li>{@code retry_then_stop}：失败后按 retryCount 重试,全部失败后停止</li>
 *     </ul>
 *   </li>
 *   <li><b>优雅关闭</b>：PreDestroy 时调用 shutdownNow() + awaitTermination</li>
 * </ul>
 *
 * <p>与 {@code TaskProducer} 是两条独立执行路径,根据部署场景二选一或共存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutorService {

    private final TestTaskMapper testTaskMapper;
    private final TestTaskRunMapper testTaskRunMapper;
    /** 用 ObjectProvider 避免与 CaseRunner 循环依赖 */
    private final ObjectProvider<CaseRunner> caseRunnerProvider;
    private final ExecutionSupportService executionSupport;
    private final CaseMapper caseMapper;
    private final ReportGenerationService reportGenerationService;
    private final EngineProperties engineProperties;

    /** 并行执行池（懒初始化,首次使用时按 parallelPoolSize 创建） */
    private volatile ExecutorService parallelExecutor;

    @PostConstruct
    public void init() {
        log.info("TaskExecutorService 已就绪");
    }

    @PreDestroy
    public void shutdown() {
        if (parallelExecutor != null) {
            parallelExecutor.shutdownNow();
            try {
                if (!parallelExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("并行池未在 10s 内关闭完成,可能存在残留线程");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 执行任务（按任务的 executeMode + failStrategy 决定执行方式）。
     *
     * <p>流程：解析 caseIds → 为每个用例预建 run 记录 → 顺序/并行执行 → 汇总生成报告。
     * 任务级变量池在此方法内通过 {@link #buildResolver} 构建并注入每个用例。
     *
     * @param task 待执行任务（含 caseIds、executeMode、failStrategy、parallelPoolSize 等）
     * @return 本次创建的用例运行记录列表（run）
     * @throws com.apiweb.common.BizException 任务或用例列表为空时抛出（参数校验）
     */
    public List<TestTaskRunEntity> execute(TestTaskEntity task) {
        if (task == null) throw BizException.badRequest("任务为空");
        List<String> caseIds = JsonUtils.fromJson(
                task.getCaseIds() == null ? "[]" : task.getCaseIds(), List.class);
        if (caseIds.isEmpty()) {
            throw BizException.badRequest("任务未包含用例");
        }

        boolean parallel = ExecuteMode.parseOrDefault(task.getExecuteMode()) == ExecuteMode.PARALLEL;
        int poolSize = clampPoolSize(task.getParallelPoolSize());
        FailStrategy strategy = FailStrategy.parseOrDefault(task.getFailStrategy());

        // 一次性为所有用例创建 run，并在内存里保留 runId -> caseId 映射。
        // 不能用「按 startedAt 排序后取下标」反推用例：同毫秒创建的多条 run 排序不稳定，
        // 会导致执行了错误的用例（历史缺陷：断言失败的用例被报告为通过）。
        List<TestTaskRunEntity> runs = new ArrayList<>();
        Map<String, String> runCaseIds = new LinkedHashMap<>();
        for (String caseId : caseIds) {
            TestTaskRunEntity run = new TestTaskRunEntity();
            run.setTaskId(task.getId());
            run.setResult(RunStatus.PENDING.getCode());
            run.setStartedAt(Instant.now());
            run.setDetails("[]");
            testTaskRunMapper.insert(run);
            runs.add(run);
            runCaseIds.put(run.getId(), caseId);
        }

        if (parallel) {
            executeParallel(task, runs, runCaseIds, poolSize, strategy);
        } else {
            executeSequential(task, runs, runCaseIds, strategy);
        }

        // 执行完成后汇总生成接口测试报告（t_report + t_report_detail），
        // 否则「测试报告」页将无数据。
        try {
            generateReport(task, runs, caseIds);
        } catch (Exception e) {
            log.error("生成任务报告失败: taskId={}", task.getId(), e);
        }
        return runs;
    }

    /**
     * 汇总本轮 runs 生成一条接口测试报告。
     */
    private void generateReport(TestTaskEntity task, List<TestTaskRunEntity> runs,
                                List<String> caseIds) {
        // 组装 runId -> 用例名 映射
        java.util.Map<String, String> runNameMap = new java.util.LinkedHashMap<>();
        // runs 与 caseIds 顺序一致（execute 中按顺序创建 run）
        for (int i = 0; i < runs.size() && i < caseIds.size(); i++) {
            CaseEntity c = caseMapper.selectById(caseIds.get(i));
            runNameMap.put(runs.get(i).getId(), c == null ? caseIds.get(i) : c.getName());
        }
        reportGenerationService.generateFromRuns(
                task.getProjectId(), task.getId(), task.getName(),
                TriggerType.MANUAL.getCode(), null, runNameMap, runs);
    }

    // ============== 顺序执行 ==============

    private void executeSequential(TestTaskEntity task, List<TestTaskRunEntity> runs,
                                   Map<String, String> runCaseIds, FailStrategy strategy) {
        boolean stopOnFail = strategy.stopOnFailure();
        for (TestTaskRunEntity run : runs) {
            boolean ok = runOne(task, run, runCaseIds.get(run.getId()));
            if (!ok && stopOnFail) {
                log.info("任务 {} 因失败策略 {} 提前终止", task.getId(), strategy.getCode());
                int idx = runs.indexOf(run);
                for (int i = idx + 1; i < runs.size(); i++) {
                    TestTaskRunEntity skipped = runs.get(i);
                    if (RunStatus.PENDING.getCode().equals(skipped.getResult())) {
                        skipped.setResult(RunStatus.SKIPPED.getCode());
                        testTaskRunMapper.updateById(skipped);
                    }
                }
                return;
            }
        }
    }

    // ============== 并行执行 ==============

    private void executeParallel(TestTaskEntity task, List<TestTaskRunEntity> runs,
                                 Map<String, String> runCaseIds, int poolSize, FailStrategy strategy) {
        ExecutorService executor = getOrCreateExecutor(poolSize);
        AtomicInteger failureCount = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        boolean stopOnFail = strategy.stopOnFailure();

        for (TestTaskRunEntity run : runs) {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                if (stopOnFail && failureCount.get() > 0) {
                    if (RunStatus.PENDING.getCode().equals(run.getResult())) {
                        run.setResult(RunStatus.SKIPPED.getCode());
                        testTaskRunMapper.updateById(run);
                    }
                    return;
                }
                boolean ok = runOne(task, run, runCaseIds.get(run.getId()));
                if (!ok) {
                    failureCount.incrementAndGet();
                }
            }, executor);
            futures.add(f);
        }

        // 等所有用例结束（即使失败也等,确保 run 状态完整）
        try {
            long base = engineProperties.getParallelWaitTimeoutSeconds();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(Math.max(base, runs.size() / 10L + 1), TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            log.warn("任务 {} 并行执行异常: {}", task.getId(), e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    /**
     * 懒初始化并行执行池（双重检查锁，保证线程安全且只创建一次）。
     *
     * <p>线程设为守护线程：JVM 退出时不会因池内线程未结束而阻塞关闭；
     * 拒绝策略用 {@code AbortPolicy}，队列满时直接抛异常由上层捕获，
     * 而不是静默丢弃用例（避免「任务看起来成功但用例没跑」）。
     *
     * @param poolSize 线程池核心/最大线程数（已在外部 clamp 到合法范围）
     * @return 复用的并行执行池
     */
    private ExecutorService getOrCreateExecutor(int poolSize) {
        ExecutorService local = parallelExecutor;
        if (local == null || local.isShutdown()) {
            synchronized (this) {
                local = parallelExecutor;
                if (local == null || local.isShutdown()) {
                    local = new ThreadPoolExecutor(
                            poolSize, poolSize,
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(engineProperties.getExecutorQueueCapacity()),
                            r -> {
                                Thread t = new Thread(r, "task-executor-" + System.nanoTime());
                                t.setDaemon(true);
                                return t;
                            },
                            new ThreadPoolExecutor.AbortPolicy());
                    parallelExecutor = local;
                    log.info("创建并行执行池: poolSize={}", poolSize);
                }
            }
        }
        return local;
    }

    /**
     * 把用户配置的并行池大小收敛到合法区间 [1, max]。
     *
     * <p>下限防止 poolSize<=0 导致线程池无法接受任务；上限防止误配超大并发拖垮机器。
     *
     * @param raw 任务里配置的并行池大小（可能为 null）
     * @return 收敛后的实际池大小
     */
    private int clampPoolSize(Integer raw) {
        if (raw == null) return engineProperties.getDefaultParallelPoolSize();
        if (raw < 1) return 1;
        int max = engineProperties.getMaxParallelPoolSize();
        return Math.min(raw, max);
    }

    /**
     * 执行单个用例 run。
     */
    private boolean runOne(TestTaskEntity task, TestTaskRunEntity run, String caseId) {
        long start = System.currentTimeMillis();
        run.setResult(RunStatus.RUNNING.getCode());
        testTaskRunMapper.updateById(run);
        try {
            CaseRunner runner = caseRunnerProvider.getIfAvailable();
            if (runner == null) {
                run.setResult(RunStatus.ERROR.getCode());
                run.setDetails("[{\"error\":\"CaseRunner 未注册\"}]");
                run.setEndedAt(Instant.now());
                run.setDuration((int) (System.currentTimeMillis() - start));
                testTaskRunMapper.updateById(run);
                return false;
            }
            VariablesResolver resolver = buildResolver(task);
            EngineDtos.ExecutionResult result = runner.runCaseSteps(caseId, resolver);
            run.setResult(result.allPassed()
                    ? RunStatus.SUCCESS.getCode() : RunStatus.FAILED.getCode());
            run.setDetails(JsonUtils.toJson(result.getSteps()));
            run.setDuration((int) (System.currentTimeMillis() - start));
            run.setEndedAt(Instant.now());
            testTaskRunMapper.updateById(run);
            return result.allPassed();
        } catch (Exception e) {
            log.error("用例执行异常: runId={}", run.getId(), e);
            run.setResult(RunStatus.ERROR.getCode());
            run.setDetails("[{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}]");
            run.setDuration((int) (System.currentTimeMillis() - start));
            run.setEndedAt(Instant.now());
            testTaskRunMapper.updateById(run);
            return false;
        }
    }

    /**
     * 组装执行环境变量池（task 级覆盖 + 环境变量 + 全局变量）。
     */
    private VariablesResolver buildResolver(TestTaskEntity task) {
        Map<String, String> caseVarsMap = executionSupport.kvToMap(task.getVariables());
        List<VariableMerger.CaseVariable> caseVars = new ArrayList<>();
        caseVarsMap.forEach((k, v) -> caseVars.add(new VariableMerger.CaseVariable(k, v)));
        VariablesResolver resolver = executionSupport.buildMergedResolver(
                task.getProjectId(),
                task.getEnvironmentId(),
                caseVars,
                Map.of());
        if (task.getBaseUrl() != null && !task.getBaseUrl().isBlank()) {
            resolver.put("baseUrl", task.getBaseUrl());
        }
        return resolver;
    }

    /**
     * 列出任务最近的 runs（便捷方法）。
     */
    public List<TestTaskRunEntity> listRuns(String taskId) {
        return testTaskRunMapper.selectList(
                new LambdaQueryWrapper<TestTaskRunEntity>()
                        .eq(TestTaskRunEntity::getTaskId, taskId)
                        .orderByDesc(TestTaskRunEntity::getStartedAt)
                        .last("LIMIT " + engineProperties.getListMaxLimit()));
    }

    /**
     * 列出当前活跃任务（便捷查询）。
     *
     * <p>当前实现返回空列表，活跃任务的真实判断已下沉到执行机和调度层，
     * 保留方法仅作占位以兼容调用方接口。
     *
     * @return 活跃任务列表（当前恒为空）
     */
    public List<TestTaskRunEntity> listActiveTasks() {
        return Collections.emptyList();
    }
}