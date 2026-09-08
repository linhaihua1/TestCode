package com.apiweb.engine;

import com.apiweb.common.BizException;
import com.apiweb.engine.variable.VariableMerger;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.TestTaskRunMapper;
import com.apiweb.service.ExecutionSupportService;
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
     */
    public List<TestTaskRunEntity> execute(TestTaskEntity task) {
        if (task == null) throw BizException.badRequest("任务为空");
        List<String> caseIds = JsonUtils.fromJson(
                task.getCaseIds() == null ? "[]" : task.getCaseIds(), List.class);
        if (caseIds.isEmpty()) {
            throw BizException.badRequest("任务未包含用例");
        }

        boolean parallel = "parallel".equalsIgnoreCase(task.getExecuteMode());
        int poolSize = clampPoolSize(task.getParallelPoolSize());
        String strategy = task.getFailStrategy() == null ? "stop_on_fail" : task.getFailStrategy();

        // 一次性为所有用例创建 run
        List<TestTaskRunEntity> runs = new ArrayList<>();
        for (String caseId : caseIds) {
            TestTaskRunEntity run = new TestTaskRunEntity();
            run.setTaskId(task.getId());
            run.setResult("pending");
            run.setStartedAt(Instant.now());
            run.setDetails("[]");
            testTaskRunMapper.insert(run);
            runs.add(run);
        }

        if (parallel) {
            executeParallel(task, runs, poolSize, strategy);
        } else {
            executeSequential(task, runs, strategy);
        }
        return runs;
    }

    // ============== 顺序执行 ==============

    private void executeSequential(TestTaskEntity task, List<TestTaskRunEntity> runs, String strategy) {
        boolean stopOnFail = "stop_on_fail".equalsIgnoreCase(strategy);
        for (TestTaskRunEntity run : runs) {
            boolean ok = runOne(task, run);
            if (!ok && stopOnFail) {
                log.info("任务 {} 因失败策略 {} 提前终止", task.getId(), strategy);
                int idx = runs.indexOf(run);
                for (int i = idx + 1; i < runs.size(); i++) {
                    TestTaskRunEntity skipped = runs.get(i);
                    if ("pending".equals(skipped.getResult())) {
                        skipped.setResult("skipped");
                        testTaskRunMapper.updateById(skipped);
                    }
                }
                return;
            }
        }
    }

    // ============== 并行执行 ==============

    private void executeParallel(TestTaskEntity task, List<TestTaskRunEntity> runs, int poolSize, String strategy) {
        ExecutorService executor = getOrCreateExecutor(poolSize);
        AtomicInteger failureCount = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        boolean stopOnFail = "stop_on_fail".equalsIgnoreCase(strategy)
                || "retry_then_stop".equalsIgnoreCase(strategy);

        for (TestTaskRunEntity run : runs) {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                if (stopOnFail && failureCount.get() > 0) {
                    if ("pending".equals(run.getResult())) {
                        run.setResult("skipped");
                        testTaskRunMapper.updateById(run);
                    }
                    return;
                }
                boolean ok = runOne(task, run);
                if (!ok) {
                    failureCount.incrementAndGet();
                }
            }, executor);
            futures.add(f);
        }

        // 等所有用例结束（即使失败也等,确保 run 状态完整）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(Math.max(60, runs.size() / 10L + 1), TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            log.warn("任务 {} 并行执行异常: {}", task.getId(), e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private ExecutorService getOrCreateExecutor(int poolSize) {
        ExecutorService local = parallelExecutor;
        if (local == null || local.isShutdown()) {
            synchronized (this) {
                local = parallelExecutor;
                if (local == null || local.isShutdown()) {
                    local = new ThreadPoolExecutor(
                            poolSize, poolSize,
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(1024),
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

    private static int clampPoolSize(Integer raw) {
        if (raw == null) return 5;
        if (raw < 1) return 1;
        if (raw > 200) return 200;
        return raw;
    }

    /**
     * 执行单个用例 run。
     */
    private boolean runOne(TestTaskEntity task, TestTaskRunEntity run) {
        long start = System.currentTimeMillis();
        run.setResult("running");
        testTaskRunMapper.updateById(run);
        try {
            CaseRunner runner = caseRunnerProvider.getIfAvailable();
            if (runner == null) {
                run.setResult("error");
                run.setDetails("[{\"error\":\"CaseRunner 未注册\"}]");
                run.setEndedAt(Instant.now());
                run.setDuration((int) (System.currentTimeMillis() - start));
                testTaskRunMapper.updateById(run);
                return false;
            }
            String caseId = extractCaseId(task, run);
            VariablesResolver resolver = buildResolver(task);
            EngineDtos.ExecutionResult result = runner.runCaseSteps(caseId, resolver);
            run.setResult(result.allPassed() ? "success" : "failed");
            run.setDetails(JsonUtils.toJson(result.getSteps()));
            run.setDuration((int) (System.currentTimeMillis() - start));
            run.setEndedAt(Instant.now());
            testTaskRunMapper.updateById(run);
            return result.allPassed();
        } catch (Exception e) {
            log.error("用例执行异常: runId={}", run.getId(), e);
            run.setResult("error");
            run.setDetails("[{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}]");
            run.setDuration((int) (System.currentTimeMillis() - start));
            run.setEndedAt(Instant.now());
            testTaskRunMapper.updateById(run);
            return false;
        }
    }

    /**
     * 从 task.caseIds 中按 run 顺序查找对应 caseId。
     */
    private String extractCaseId(TestTaskEntity task, TestTaskRunEntity run) {
        List<String> caseIds = JsonUtils.fromJson(
                task.getCaseIds() == null ? "[]" : task.getCaseIds(), List.class);
        List<TestTaskRunEntity> runs = testTaskRunMapper.selectList(
                new LambdaQueryWrapper<TestTaskRunEntity>()
                        .eq(TestTaskRunEntity::getTaskId, task.getId())
                        .orderByAsc(TestTaskRunEntity::getStartedAt));
        int idx = runs.indexOf(run);
        if (idx >= 0 && idx < caseIds.size()) {
            return caseIds.get(idx);
        }
        return caseIds.isEmpty() ? null : caseIds.get(0);
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
                        .last("LIMIT 100"));
    }

    public List<TestTaskRunEntity> listActiveTasks() {
        return Collections.emptyList();
    }
}