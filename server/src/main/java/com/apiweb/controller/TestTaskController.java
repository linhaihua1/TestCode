package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.TestTaskRunMapper;
import com.apiweb.mq.TaskProducer;
import com.apiweb.security.UserContext;
import com.apiweb.service.OssService;
import com.apiweb.service.TaskScheduleService;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * 测试任务：编排用例集合，支持手动触发（RabbitMQ 异步投递）与 Quartz 定时调度。
 */
@RestController
@RequestMapping("/api/test-tasks")
@RequiredArgsConstructor
public class TestTaskController {

    private final TestTaskMapper testTaskMapper;
    private final TestTaskRunMapper testTaskRunMapper;
    private final TaskProducer taskProducer;
    private final TaskScheduleService taskScheduleService;
    private final OssService ossService;

    @GetMapping
    public Result<List<TestTaskEntity>> list(@RequestParam String projectId) {
        return Result.ok(testTaskMapper.selectList(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getProjectId, projectId)
                        .isNull(TestTaskEntity::getDeletedAt)
                        .orderByDesc(TestTaskEntity::getUpdatedAt)));
    }

    @GetMapping("/{id}")
    public Result<TestTaskEntity> get(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return Result.ok(task);
    }

    @AuditLog(action = "create", entityType = "test_task")
    @PostMapping
    public Result<TestTaskEntity> create(@RequestBody TestTaskEntity task) {
        if (task.getName() == null || task.getName().isBlank()) {
            throw BizException.badRequest("任务名称必填");
        }
        if (task.getCaseIds() == null) {
            task.setCaseIds("[]");
        }
        if (task.getVariables() == null) {
            task.setVariables("[]");
        }
        task.setCreatedBy(UserContext.username());
        testTaskMapper.insert(task);
        taskScheduleService.schedule(task);
        return Result.ok(task);
    }

    @AuditLog(action = "update", entityType = "test_task")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody TestTaskEntity task) {
        if (testTaskMapper.selectById(id) == null) {
            throw BizException.notFound("任务不存在");
        }
        task.setId(id);
        testTaskMapper.updateById(task);
        taskScheduleService.schedule(task);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "test_task")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task != null) {
            task.setDeletedAt(Instant.now());
            testTaskMapper.updateById(task);
        }
        taskScheduleService.remove(id);
        return Result.ok();
    }

    /**
     * 手动触发：为每个用例创建 run 并投递 RabbitMQ 异步执行。
     */
    @AuditLog(action = "execute", entityType = "test_task")
    @PostMapping("/{id}/run")
    public Result<List<TestTaskRunEntity>> run(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        List<String> caseIds = JsonUtils.fromJson(
                task.getCaseIds() == null ? "[]" : task.getCaseIds(), List.class);
        List<TestTaskRunEntity> runs = new java.util.ArrayList<>();
        for (String caseId : caseIds) {
            TestTaskRunEntity run = new TestTaskRunEntity();
            run.setTaskId(id);
            run.setResult("pending");
            run.setStartedAt(Instant.now());
            run.setDetails("[]");
            testTaskRunMapper.insert(run);
            taskProducer.sendApiTask(run.getId(), task.getProjectId(), id, caseId,
                    task.getEnvironmentId());
            runs.add(run);
        }
        return Result.ok(runs);
    }

    @GetMapping("/{id}/runs")
    public Result<List<TestTaskRunEntity>> runs(@PathVariable String id) {
        List<TestTaskRunEntity> list = testTaskRunMapper.selectList(
                new LambdaQueryWrapper<TestTaskRunEntity>()
                        .eq(TestTaskRunEntity::getTaskId, id)
                        .orderByDesc(TestTaskRunEntity::getStartedAt)
                        .last("LIMIT 100"));
        // MinIO 引用转内联（前端直接渲染）
        for (TestTaskRunEntity run : list) {
            if (run.getDetails() != null && run.getDetails().startsWith("minio://")) {
                run.setDetails(ossService.get(run.getDetails()));
            }
        }
        return Result.ok(list);
    }

    /**
     * 任务启停（同步 Quartz）。
     */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable String id) {
        TestTaskEntity task = testTaskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        testTaskMapper.updateById(task);
        taskScheduleService.schedule(task);
        return Result.ok();
    }
}
