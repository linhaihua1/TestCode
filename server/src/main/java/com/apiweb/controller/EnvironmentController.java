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

    /**
     * 查询指定项目的全部环境配置（按创建时间升序）。
     *
     * @param projectId 项目 ID（必填，query 参数）
     * @return 环境列表（每个环境含变量、公共 headers 等）
     */
    @GetMapping
    public Result<List<EnvironmentEntity>> list(@RequestParam String projectId) {
        return Result.ok(environmentMapper.selectList(
                new LambdaQueryWrapper<EnvironmentEntity>()
                        .eq(EnvironmentEntity::getProjectId, projectId)
                        .orderByAsc(EnvironmentEntity::getCreatedAt)));
    }

    /**
     * 新建环境：补齐 variables/headers 默认空数组后落库。
     *
     * @param env 环境实体（请求体），projectId 与 name 为必填
     * @return 已落库的环境实体
     * @throws BizException projectId 或 name 缺失时抛 badRequest
     * @audit 写审计日志：action=create, entityType=environment
     */
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

    /**
     * 更新环境配置（覆盖请求体可写字段）。
     *
     * @param id  环境 ID（路径参数，必填）
     * @param env 环境实体（请求体）
     * @return 无业务数据
     * @throws BizException 环境不存在时抛 notFound
     * @audit 写审计日志：action=update, entityType=environment
     */
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

    /**
     * 删除环境（物理删除，区别于用例的软删除）。
     *
     * @param id 环境 ID（路径参数，必填）
     * @return 无业务数据
     */
    @AuditLog(action = "delete", entityType = "environment")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        environmentMapper.deleteById(id);
        return Result.ok();
    }
}
