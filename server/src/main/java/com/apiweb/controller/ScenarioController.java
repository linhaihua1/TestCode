package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.scenario.ScenarioReq;
import com.apiweb.entity.ReportEntity;
import com.apiweb.service.ScenarioService;
import com.apiweb.vo.scenario.ScenarioVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 接口场景管理 + 场景执行。
 */
@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

    /**
     * 查询项目的场景列表（按更新时间倒序，不含步骤）。
     *
     * @param projectId 项目 ID（必填）
     * @return 场景列表
     */
    @GetMapping
    public Result<List<ScenarioVO>> list(@RequestParam String projectId) {
        return Result.ok(scenarioService.list(projectId));
    }

    /**
     * 获取场景详情（含步骤列表，按排序升序）。
     *
     * @param id 场景 ID（路径参数，必填）
     * @return 场景 VO（含 steps）
     */
    @GetMapping("/{id}")
    public Result<ScenarioVO> get(@PathVariable String id) {
        return Result.ok(scenarioService.get(id));
    }

    /**
     * 新建场景（含步骤）。
     *
     * @param req 场景请求 DTO
     * @return 已落库的场景 VO
     * @audit 写审计日志：action=create, entityType=scenario
     */
    @AuditLog(action = "create", entityType = "scenario")
    @PostMapping
    public Result<ScenarioVO> create(@Valid @RequestBody ScenarioReq req) {
        return Result.ok(scenarioService.create(req));
    }

    /**
     * 更新场景（含可选的步骤整体替换）。
     *
     * @param id  场景 ID（路径参数，必填）
     * @param req 场景请求 DTO
     * @return 无业务数据
     * @audit 写审计日志：action=update, entityType=scenario
     */
    @AuditLog(action = "update", entityType = "scenario")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody ScenarioReq req) {
        scenarioService.update(id, req);
        return Result.ok();
    }

    /**
     * 删除场景：先删其步骤再删主表。
     *
     * @param id 场景 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=scenario
     */
    @AuditLog(action = "delete", entityType = "scenario")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        scenarioService.delete(id);
        return Result.ok();
    }

    /**
     * 执行场景：按步骤顺序逐个执行并汇总生成报告（同步执行）。
     *
     * @param id   场景 ID（路径参数，必填）
     * @param body 请求体（可选），可含 environmentId 指定执行环境
     * @return 执行后生成的报告实体
     * @audit 写审计日志：action=execute, entityType=scenario
     */
    @AuditLog(action = "execute", entityType = "scenario")
    @PostMapping("/{id}/execute")
    public Result<ReportEntity> execute(@PathVariable String id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        String environmentId = body == null || body.get("environmentId") == null ? null
                : String.valueOf(body.get("environmentId"));
        return Result.ok(scenarioService.execute(id, environmentId));
    }
}
