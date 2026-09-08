package com.apiweb.service;

import com.apiweb.job.RecycleBinAutoCleanJob;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 回收站自动清理调度：每天凌晨 03:00 触发 {@link RecycleBinAutoCleanJob}。
 *
 * <p>localdev profile 下 Quartz 自动配置被排除，Scheduler bean 不存在，所有方法静默跳过；
 * 生产环境（启用 Quartz 集群模式）会自动注册。
 *
 * <h3>Cron 说明</h3>
 * Quartz 风格：{@code "0 0 3 * * ?"} = 每天 03:00:00（秒 分 时 日 月 周）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleBinScheduleService {

    public static final String JOB_NAME = "recycle-bin-auto-clean";
    public static final String TRIGGER_NAME = "recycle-bin-auto-clean-trigger";
    public static final String CRON = "0 0 3 * * ?";

    private final ObjectProvider<Scheduler> schedulerProvider;

    private Scheduler scheduler() {
        return schedulerProvider.getIfAvailable();
    }

    @PostConstruct
    public void register() {
        Scheduler scheduler = scheduler();
        if (scheduler == null) {
            log.warn("[RecycleBinScheduleService] Scheduler 不可用（Quartz 未启用），跳过回收站定时清理注册");
            return;
        }
        try {
            JobKey jobKey = new JobKey(JOB_NAME);
            TriggerKey triggerKey = new TriggerKey(TRIGGER_NAME);
            // 已存在则跳过（避免覆盖运行时调度）
            if (scheduler.checkExists(jobKey)) {
                log.info("[RecycleBinScheduleService] 回收站定时清理 Job 已存在，跳过注册");
                return;
            }
            JobDetail job = JobBuilder.newJob(RecycleBinAutoCleanJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(CRON)
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();
            scheduler.scheduleJob(job, trigger);
            log.info("[RecycleBinScheduleService] 已注册回收站定时清理 Job，每天 03:00 触发");
        } catch (SchedulerException e) {
            log.error("[RecycleBinScheduleService] 注册回收站定时清理 Job 失败", e);
        }
    }
}