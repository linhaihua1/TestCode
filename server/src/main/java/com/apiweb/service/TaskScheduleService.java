package com.apiweb.service;

import com.apiweb.entity.TestTaskEntity;
import com.apiweb.job.TestTaskScheduleJob;
import com.apiweb.mapper.TestTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Quartz 调度管理：任务保存/启停时同步注册或移除 Quartz Trigger（集群模式）。
 * <p>localdev profile 下 Quartz 自动配置被排除，Scheduler bean 不存在，所有方法静默跳过；
 * 不会影响其它业务（接口/UI/性能测试仍可手动触发执行）。
 */
@Slf4j
@Service
public class TaskScheduleService {

    private final ObjectProvider<Scheduler> schedulerProvider;
    private final TestTaskMapper testTaskMapper;

    public TaskScheduleService(ObjectProvider<Scheduler> schedulerProvider,
                               TestTaskMapper testTaskMapper) {
        this.schedulerProvider = schedulerProvider;
        this.testTaskMapper = testTaskMapper;
    }

    /**
     * 惰性获取 Quartz Scheduler（集群模式由 Spring 注入）。
     *
     * <p>使用 {@link ObjectProvider#getIfAvailable()} 而非直接注入，是为了兼容
     * localdev profile 下 Quartz 自动配置被排除、Scheduler bean 不存在的场景：
     * 此时返回 null，上层方法据此静默跳过调度相关逻辑。</p>
     *
     * @return Quartz Scheduler，未启用时返回 null
     */
    private Scheduler scheduler() {
        return schedulerProvider.getIfAvailable();
    }

    /**
     * 启动时同步所有带 cron 且启用的任务到 Quartz（集群安全：重复注册会覆盖）。
     */
    @PostConstruct
    public void syncAll() {
        Scheduler scheduler = scheduler();
        if (scheduler == null) {
            log.warn("[TaskScheduleService] Scheduler bean 不可用（Quartz 未启用），跳过定时任务同步");
            return;
        }
        try {
            List<TestTaskEntity> tasks = testTaskMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestTaskEntity>()
                            .isNotNull(TestTaskEntity::getCronExpr)
                            .eq(TestTaskEntity::getEnabled, true)
                            .isNull(TestTaskEntity::getDeletedAt));
            for (TestTaskEntity task : tasks) {
                schedule(task);
            }
            log.info("Quartz 调度已同步 {} 个定时测试任务", tasks.size());
        } catch (Exception e) {
            log.error("Quartz 调度同步失败", e);
        }
    }

    /**
     * 注册或更新一个测试任务的 Quartz 定时调度。
     *
     * <p>行为：
     * <ul>
     *   <li>cron 为空、或任务未启用、或任务已软删除 → 调用 {@link #remove} 取消已有调度（幂等）；</li>
     *   <li>否则以 {@code test-task-{id}} 为 JobKey 注册 CronTrigger，
     *       使用 {@code FireAndProceed} 处理错过触发（misfire 时立即补跑一次）；</li>
     *   <li>集群模式下重复注册会覆盖原 Trigger，天然幂等，可安全在 @PostConstruct 与任务保存时重复调用。</li>
     * </ul>
     *
     * <p>副作用：向 Quartz 写入/更新 JobDetail 与 Trigger；Scheduler 不可用时（localdev）直接返回。</p>
     *
     * @param task 测试任务实体（需含 id / name / cronExpr / enabled）
     */
    public void schedule(TestTaskEntity task) {
        Scheduler scheduler = scheduler();
        if (scheduler == null) return;
        if (task.getCronExpr() == null || task.getCronExpr().isBlank()
                || !Boolean.TRUE.equals(task.getEnabled())) {
            remove(task.getId());
            return;
        }
        try {
            JobDetail jobDetail = JobBuilder.newJob(TestTaskScheduleJob.class)
                    .withIdentity(jobKey(task.getId()))
                    .usingJobData(TestTaskScheduleJob.DATA_TASK_ID, task.getId())
                    .storeDurably()
                    .build();
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(task.getId()))
                    .forJob(jobDetail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpr())
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
            log.info("已注册定时任务: {} cron={}", task.getName(), task.getCronExpr());
        } catch (SchedulerException e) {
            log.error("注册定时任务失败: {}", task.getId(), e);
        }
    }

    /**
     * 移除某任务的 Quartz 调度（任务删除 / 停用 / cron 清空时调用）。
     *
     * <p>副作用：从 Quartz 删除该 Job（含其 Trigger），不再触发；
     * Scheduler 不可用时（localdev）或删除失败仅告警，不影响主流程。</p>
     *
     * @param taskId 测试任务 ID
     */
    public void remove(String taskId) {
        Scheduler scheduler = scheduler();
        if (scheduler == null) return;
        try {
            scheduler.deleteJob(jobKey(taskId));
        } catch (SchedulerException e) {
            log.warn("移除调度失败: {}", taskId);
        }
    }

    private JobKey jobKey(String taskId) {
        return JobKey.jobKey("test-task-" + taskId, "apiweb");
    }

    private TriggerKey triggerKey(String taskId) {
        return TriggerKey.triggerKey("test-task-trigger-" + taskId, "apiweb");
    }
}
