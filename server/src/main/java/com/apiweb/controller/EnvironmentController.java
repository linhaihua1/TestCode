package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.EnvironmentEntity;
import com.apiweb.mapper.EnvironmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private final EnvironmentMapper environmentMapper;

    @GetMapping
    public Result<List<EnvironmentEntity>> list(@RequestParam String projectId) {
        return Result.ok(environmentMapper.selectList(
                new LambdaQueryWrapper<EnvironmentEntity>()
                        .eq(EnvironmentEntity::getProjectId, projectId)
                        .orderByAsc(EnvironmentEntity::getCreatedAt)));
    }

    @AuditLog(action = "create", entityType = "environment")
    @PostMapping
    public Result<EnvironmentEntity> create(@RequestBody EnvironmentEntity env) {
        if (env.getProjectId() == null || env.getName() == null) {
            throw BizException.badRequest("projectId 与 name 必填");
        }
        if (env.getVariables() == null) {
            env.setVariables("[]");
        }
        if (env.getHeaders() == null) {
            env.setHeaders("[]");
        }
        environmentMapper.insert(env);
        return Result.ok(env);
    }

    @AuditLog(action = "update", entityType = "environment")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody EnvironmentEntity env) {
        if (environmentMapper.selectById(id) == null) {
            throw BizException.notFound("环境不存在");
        }
        env.setId(id);
        environmentMapper.updateById(env);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "environment")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        environmentMapper.deleteById(id);
        return Result.ok();
    }
}
