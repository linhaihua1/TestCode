package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.TestTaskConverter;
import com.apiweb.dto.testtask.TestTaskReq;
import com.apiweb.engine.RunStatus;
import com.apiweb.engine.TaskExecutorService;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.TestTaskRunMapper;
import com.apiweb.mq.TaskProducer;
import com.apiweb.security.UserContext;
import com.apiweb.service.OssService;
import com.apiweb.service.TaskScheduleService;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.testtask.TestTaskVO;
import com.apiweb.vo.testtask.WebhookTokenVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试任务业务服务：编排用例集合，支持手动触发 / 本地并行执行 / 定时调度 / CI/CD webhook 触发。
 */
@Service
@RequiredArgsConstructor
public class TestTaskService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final TestTaskMapper testTaskMapper;
    private final TestTaskRunMapper testTaskRunMapper;
    private final TestTaskConverter testTaskConverter;
    private final TaskProducer taskProducer;
    private final TaskScheduleService taskScheduleService;
    private final TaskExecutorService taskExecutorService;
    private final OssService ossService;

    /**
     * 查询项目的测试任务列表（仅未删除，按更新时间倒序）。
     */
    public List<TestTaskVO> list(String projectId) {
        return testTaskConverter.toVOList(testTaskMapper.selectList(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getProjectId, projectId)
                        .isNull(TestTaskEntity::getDeletedAt)
                        .orderByDesc(TestTaskEntity::getUpdatedAt)));
    }

    /**
     * 获取测试任务详情。
     */
    public TestTaskVO get(String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return testTaskConverter.toVO(task);
    }

    /**
     * 新建测试任务：补齐默认值并校验后落库，同时注册定时调度。
     */
    @Transactional
    public TestTaskVO create(TestTaskReq req) {
        TestTaskEntity task = testTaskConverter.toEntity(req);
        initDefaults(task);
        validate(task);
        task.setCreatedBy(UserContext.username());
        testTaskMapper.insert(task);
        taskScheduleService.schedule(task);
        return testTaskConverter.toVO(task);
    }

    /**
     * 更新测试任务：未传字段回填原值，校验后落库并重新注册定时调度。
     */
    @Transactional
    public void update(String id, TestTaskReq req) {
        TestTaskEntity origin = testTaskMapper.selectById(id);
        if (origin == null) {
            throw BizException.notFound("任务不存在");
        }
        TestTaskEntity task = testTaskConverter.toEntity(req);
        task.setId(id);
        mergeDefaults(task, origin);
        validate(task);
        testTaskMapper.updateById(task);
        taskScheduleService.schedule(task);
    }

    /**
     * 软删除测试任务：打 deletedAt 并移除其定时调度。
     */
    @Transactional
    public void delete(String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task != null) {
            task.setDeletedAt(Instant.now());
            testTaskMapper.updateById(task);
        }
        taskScheduleService.remove(id);
    }

    /**
     * 异步触发（投递 RabbitMQ）：为每个用例创建 PENDING 运行记录并发送 MQ。
     */
    @Transactional
    public List<TestTaskRunEntity> run(String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        List<String> caseIds = JsonUtils.fromJson(
                task.getCaseIds() == null ? "[]" : task.getCaseIds(), List.class);
        List<TestTaskRunEntity> runs = new ArrayList<>();
        for (String caseId : caseIds) {
            TestTaskRunEntity run = new TestTaskRunEntity();
            run.setTaskId(id);
            run.setResult(RunStatus.PENDING.getCode());
            run.setStartedAt(Instant.now());
            run.setDetails("[]");
            testTaskRunMapper.insert(run);
            taskProducer.sendApiTask(run.getId(), task.getProjectId(), id, caseId,
                    task.getEnvironmentId());
            runs.add(run);
        }
        return runs;
    }

    /**
     * 同步本地执行（走 TaskExecutorService）。
     */
    public List<TestTaskRunEntity> runSync(String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return taskExecutorService.execute(task);
    }

    /**
     * 查询某任务的历史执行批次列表（details 若为 minio 引用则实时拉取回填）。
     */
    public List<TestTaskRunEntity> runs(String id) {
        List<TestTaskRunEntity> list = taskExecutorService.listRuns(id);
        for (TestTaskRunEntity run : list) {
            if (run.getDetails() != null && run.getDetails().startsWith("minio://")) {
                run.setDetails(ossService.get(run.getDetails()));
            }
        }
        return list;
    }

    /**
     * 启用/停用任务：切换 enabled 标记并同步注册或移除定时调度。
     */
    @Transactional
    public void toggle(String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        testTaskMapper.updateById(task);
        taskScheduleService.schedule(task);
    }

    /**
     * 生成/重置 webhook token（rotate 隐含启用 webhook 意图）。
     */
    @Transactional
    public WebhookTokenVO rotateWebhook(String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        String token = randomToken(32);
        task.setWebhookToken(token);
        task.setWebhookEnabled(true);
        if (task.getWebhookAutoExecute() == null) {
            task.setWebhookAutoExecute(true);
        }
        testTaskMapper.updateById(task);
        WebhookTokenVO resp = new WebhookTokenVO();
        resp.setToken(token);
        resp.setUrl("/api/v1/webhook/test-tasks/trigger?token=" + token);
        return resp;
    }

    /**
     * 禁用任务的 webhook 触发。
     */
    @Transactional
    public void disableWebhook(String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        task.setWebhookEnabled(false);
        testTaskMapper.updateById(task);
    }

    private static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private void initDefaults(TestTaskEntity task) {
        if (task.getCaseIds() == null) task.setCaseIds("[]");
        if (task.getVariables() == null) task.setVariables("[]");
        if (task.getNotifyChannels() == null) task.setNotifyChannels("[]");
        if (task.getExecuteMode() == null) task.setExecuteMode("sequential");
        if (task.getFailStrategy() == null) task.setFailStrategy("stop_on_fail");
        if (task.getParallelPoolSize() == null) task.setParallelPoolSize(5);
        if (task.getRetryCount() == null) task.setRetryCount(0);
        if (task.getTimeoutMs() == null) task.setTimeoutMs(300_000);
        if (task.getEnabled() == null) task.setEnabled(true);
        if (task.getWebhookEnabled() == null) task.setWebhookEnabled(false);
        if (task.getWebhookAutoExecute() == null) task.setWebhookAutoExecute(true);
    }

    private void mergeDefaults(TestTaskEntity task, TestTaskEntity origin) {
        if (task.getCaseIds() == null) task.setCaseIds(origin.getCaseIds());
        if (task.getVariables() == null) task.setVariables(origin.getVariables());
        if (task.getNotifyChannels() == null) task.setNotifyChannels(origin.getNotifyChannels());
        if (task.getExecuteMode() == null) task.setExecuteMode(origin.getExecuteMode());
        if (task.getFailStrategy() == null) task.setFailStrategy(origin.getFailStrategy());
        if (task.getParallelPoolSize() == null) task.setParallelPoolSize(origin.getParallelPoolSize());
        if (task.getRetryCount() == null) task.setRetryCount(origin.getRetryCount());
        if (task.getTimeoutMs() == null) task.setTimeoutMs(origin.getTimeoutMs());
        if (task.getEnabled() == null) task.setEnabled(origin.getEnabled());
        if (task.getWebhookEnabled() == null) task.setWebhookEnabled(origin.getWebhookEnabled());
        if (task.getWebhookAutoExecute() == null) task.setWebhookAutoExecute(origin.getWebhookAutoExecute());
        if (task.getEnvironmentId() == null) task.setEnvironmentId(origin.getEnvironmentId());
        if (task.getCronExpr() == null) task.setCronExpr(origin.getCronExpr());
    }

    private void validate(TestTaskEntity task) {
        if (task.getName() == null || task.getName().isBlank()) {
            throw BizException.badRequest("任务名称必填");
        }
        if (!"sequential".equalsIgnoreCase(task.getExecuteMode())
                && !"parallel".equalsIgnoreCase(task.getExecuteMode())) {
            throw BizException.badRequest("executeMode 必须是 sequential 或 parallel");
        }
        if (!List.of("stop_on_fail", "continue_all", "retry_then_stop")
                .contains(task.getFailStrategy())) {
            throw BizException.badRequest("failStrategy 不合法");
        }
        if (task.getParallelPoolSize() != null
                && (task.getParallelPoolSize() < 1 || task.getParallelPoolSize() > 200)) {
            throw BizException.badRequest("parallelPoolSize 必须在 1-200 之间");
        }
        if (task.getCronExpr() != null && !task.getCronExpr().isBlank()) {
            String[] parts = task.getCronExpr().trim().split("\\s+");
            if (parts.length != 5 && parts.length != 6 && parts.length != 7) {
                throw BizException.badRequest("cron 表达式必须是 5/6/7 段");
            }
        }
    }
}
