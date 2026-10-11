package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.testtask.TestTaskReq;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.service.TestTaskService;
import com.apiweb.vo.testtask.TestTaskVO;
import com.apiweb.vo.testtask.WebhookTokenVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试任务：编排用例集合，支持手动触发（RabbitMQ 异步）/ 本地并行执行 / Quartz 定时调度 / CI/CD webhook 触发。
 */
@RestController
@RequestMapping("/api/v1/test-tasks")
@RequiredArgsConstructor
public class TestTaskController {

    private final TestTaskService testTaskService;

    /**
     * 查询项目的测试任务列表（仅未删除，按更新时间倒序）。
     */
    @GetMapping
    public Result<List<TestTaskVO>> list(@RequestParam String projectId) {
        return Result.ok(testTaskService.list(projectId));
    }

    /**
     * 获取测试任务详情。
     */
    @GetMapping("/{id}")
    public Result<TestTaskVO> get(@PathVariable String id) {
        return Result.ok(testTaskService.get(id));
    }

    /**
     * 新建测试任务：补齐默认值并校验后落库，同时注册定时调度。
     *
     * @param req 任务请求 DTO（请求体），name 必填
     * @return 已落库的任务 VO
     * @audit 写审计日志：action=create, entityType=test_task
     */
    @AuditLog(action = "create", entityType = "test_task")
    @PostMapping
    public Result<TestTaskVO> create(@Valid @RequestBody TestTaskReq req) {
        return Result.ok(testTaskService.create(req));
    }

    /**
     * 更新测试任务：未传字段回填原值，校验后落库并重新注册定时调度。
     *
     * @param id  任务 ID（路径参数，必填）
     * @param req 任务请求 DTO（请求体）
     * @return 无业务数据
     * @audit 写审计日志：action=update, entityType=test_task
     */
    @AuditLog(action = "update", entityType = "test_task")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody TestTaskReq req) {
        testTaskService.update(id, req);
        return Result.ok();
    }

    /**
     * 软删除测试任务：打 deletedAt 并移除其定时调度。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=test_task
     */
    @AuditLog(action = "delete", entityType = "test_task")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        testTaskService.delete(id);
        return Result.ok();
    }

    /**
     * 异步触发（投递 RabbitMQ）：为每个用例创建 PENDING 运行记录并发送 MQ。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 本次触发的运行记录列表
     * @audit 写审计日志：action=execute, entityType=test_task
     */
    @AuditLog(action = "execute", entityType = "test_task")
    @PostMapping("/{id}/run")
    public Result<List<TestTaskRunEntity>> run(@PathVariable String id) {
        return Result.ok(testTaskService.run(id));
    }

    /**
     * 同步本地执行（走 TaskExecutorService，接口阻塞直到全部用例执行完毕）。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 执行批次列表
     * @audit 写审计日志：action=execute_sync, entityType=test_task
     */
    @AuditLog(action = "execute_sync", entityType = "test_task")
    @PostMapping("/{id}/run-sync")
    public Result<List<TestTaskRunEntity>> runSync(@PathVariable String id) {
        return Result.ok(testTaskService.runSync(id));
    }

    /**
     * 查询某任务的历史执行批次列表。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 执行批次列表
     */
    @GetMapping("/{id}/runs")
    public Result<List<TestTaskRunEntity>> runs(@PathVariable String id) {
        return Result.ok(testTaskService.runs(id));
    }

    /**
     * 启用/停用任务。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 无业务数据
     */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable String id) {
        testTaskService.toggle(id);
        return Result.ok();
    }

    /**
     * 生成/重置 webhook token（仅返回一次）。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return token 与完整触发 URL
     * @audit 写审计日志：action=rotate_webhook, entityType=test_task
     */
    @AuditLog(action = "rotate_webhook", entityType = "test_task")
    @PostMapping("/{id}/webhook/rotate")
    public Result<WebhookTokenVO> rotateWebhook(@PathVariable String id) {
        return Result.ok(testTaskService.rotateWebhook(id));
    }

    /**
     * 禁用任务的 webhook 触发。
     *
     * @param id 任务 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=disable_webhook, entityType=test_task
     */
    @AuditLog(action = "disable_webhook", entityType = "test_task")
    @PostMapping("/{id}/webhook/disable")
    public Result<Void> disableWebhook(@PathVariable String id) {
        testTaskService.disableWebhook(id);
        return Result.ok();
    }
}
