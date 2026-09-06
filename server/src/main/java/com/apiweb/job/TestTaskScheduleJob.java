package com.apiweb.job;

import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.TestTaskRunMapper;
import com.apiweb.mq.TaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 测试任务定时触发 Job（Quartz 集群模式，同一触发只会被集群中一个节点执行）。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class TestTaskScheduleJob implements Job {

    public static final String DATA_TASK_ID = "taskId";

    private final TestTaskMapper testTaskMapper;
    private final TestTaskRunMapper testTaskRunMapper;
    private final TaskProducer taskProducer;

    @Override
    public void execute(JobExecutionContext context) {
        String taskId = context.getMergedJobDataMap().getString(DATA_TASK_ID);
        if (taskId == null) {
            return;
        }
        TestTaskEntity task = testTaskMapper.selectById(taskId);
        if (task == null || !Boolean.TRUE.equals(task.getEnabled())) {
            log.info("定时任务跳过（任务不存在或已停用）: {}", taskId);
            return;
        }
        log.info("Quartz 触发测试任务: {} ({})", task.getName(), taskId);
        List<String> caseIds = com.apiweb.util.JsonUtils.fromJson(
                task.getCaseIds() == null ? "[]" : task.getCaseIds(), List.class);
        for (String caseId : caseIds) {
            TestTaskRunEntity run = new TestTaskRunEntity();
            run.setTaskId(taskId);
            run.setResult("pending");
            run.setStartedAt(Instant.now());
            run.setDetails("[]");
            testTaskRunMapper.insert(run);
            taskProducer.sendApiTask(run.getId(), task.getProjectId(), taskId, caseId,
                    task.getEnvironmentId());
        }
    }
}
