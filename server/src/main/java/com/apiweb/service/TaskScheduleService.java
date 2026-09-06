package com.apiweb.service;

import com.apiweb.entity.TestTaskEntity;
import com.apiweb.job.TestTaskScheduleJob;
import com.apiweb.mapper.TestTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Quartz 调度管理：任务保存/启停时同步注册或移除 Quartz Trigger（集群模式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskScheduleService {

    private final Scheduler scheduler;
    private final TestTaskMapper testTaskMapper;

    /**
     * 启动时同步所有带 cron 且启用的任务到 Quartz（集群安全：重复注册会覆盖）。
     */
    @PostConstruct
    public void syncAll() {
        try {
            List<TestTaskEntity> tasks = testTaskMapper.select(
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
     * 注册/更新一个任务的调度。
     */
    public void schedule(TestTaskEntity task) {
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
     * 移除调度（任务删除/停用时）。
     */
    public void remove(String taskId) {
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
