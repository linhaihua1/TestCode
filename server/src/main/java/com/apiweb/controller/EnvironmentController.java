package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.environment.EnvironmentReq;
import com.apiweb.service.EnvironmentService;
import com.apiweb.vo.environment.EnvironmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 环境配置管理。
 */
@RestController
@RequestMapping("/api/v1/environments")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentService environmentService;

    /**
     * 查询指定项目的全部环境配置（按创建时间升序）。
     *
     * @param projectId 项目 ID（必填，query 参数）
     * @return 环境列表（每个环境含变量、公共 headers 等）
     */
    @GetMapping
    public Result<List<EnvironmentVO>> list(@RequestParam String projectId) {
        return Result.ok(environmentService.list(projectId));
    }

    /**
     * 新建环境：补齐 variables/headers 默认空数组后落库。
     *
     * @param req 环境请求 DTO（请求体），projectId 与 name 为必填
     * @return 已落库的环境 VO
     * @audit 写审计日志：action=create, entityType=environment
     */
    @AuditLog(action = "create", entityType = "environment")
    @PostMapping
    public Result<EnvironmentVO> create(@Valid @RequestBody EnvironmentReq req) {
        return Result.ok(environmentService.create(req));
    }

    /**
     * 更新环境配置（覆盖请求体可写字段）。
     *
     * @param id  环境 ID（路径参数，必填）
     * @param req 环境请求 DTO（请求体）
     * @return 无业务数据
     * @audit 写审计日志：action=update, entityType=environment
     */
    @AuditLog(action = "update", entityType = "environment")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody EnvironmentReq req) {
        environmentService.update(id, req);
        return Result.ok();
    }

    /**
     * 删除环境（物理删除，区别于用例的软删除）。
     *
     * @param id 环境 ID（路径参数，必填）
     * @return 无业务数据
     */
    @AuditLog(action = "delete", entityType = "environment")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        environmentService.delete(id);
        return Result.ok();
    }
}
