package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 接口定义管理（接口库）。
 */
@RestController
@RequestMapping("/api/apis")
@RequiredArgsConstructor
public class ApiDefinitionController {

    private final ApiDefinitionMapper apiMapper;

    @GetMapping
    public Result<List<ApiDefinitionEntity>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<ApiDefinitionEntity> result = apiMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .eq(moduleId != null && !moduleId.isBlank(),
                                ApiDefinitionEntity::getModuleId, moduleId)
                        .like(keyword != null && !keyword.isBlank(),
                                ApiDefinitionEntity::getName, keyword)
                        .eq(method != null && !method.isBlank(),
                                ApiDefinitionEntity::getMethod, method)
                        .orderByDesc(ApiDefinitionEntity::getUpdatedAt));
        return Result.ok(result.getRecords());
    }

    @GetMapping("/{id}")
    public Result<ApiDefinitionEntity> get(@PathVariable String id) {
        ApiDefinitionEntity api = apiMapper.selectById(id);
        if (api == null) {
            throw BizException.notFound("接口不存在");
        }
        return Result.ok(api);
    }

    @AuditLog(action = "create", entityType = "api")
    @PostMapping
    public Result<ApiDefinitionEntity> create(@RequestBody ApiDefinitionEntity api) {
        if (api.getName() == null || api.getMethod() == null || api.getPath() == null) {
            throw BizException.badRequest("name/method/path 必填");
        }
        if (api.getHeaders() == null) {
            api.setHeaders("[]");
        }
        if (api.getQuery() == null) {
            api.setQuery("[]");
        }
        if (api.getTags() == null) {
            api.setTags("[]");
        }
        apiMapper.insert(api);
        return Result.ok(api);
    }

    @AuditLog(action = "update", entityType = "api")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody ApiDefinitionEntity api) {
        if (apiMapper.selectById(id) == null) {
            throw BizException.notFound("接口不存在");
        }
        api.setId(id);
        apiMapper.updateById(api);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "api")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        apiMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 批量导入（Swagger paths 简化导入）。
     */
    @AuditLog(action = "import", entityType = "api")
    @PostMapping("/import-swagger")
    @SuppressWarnings("unchecked")
    public Result<Integer> importSwagger(@RequestParam String projectId,
                                         @RequestBody Map<String, Object> swagger) {
        int count = 0;
        Object pathsObj = swagger.get("paths");
        if (pathsObj instanceof Map<?, ?> paths) {
            for (Map.Entry<?, ?> pathEntry : paths.entrySet()) {
                String path = String.valueOf(pathEntry.getKey());
                if (pathEntry.getValue() instanceof Map<?, ?> methods) {
                    for (Map.Entry<?, ?> methodEntry : methods.entrySet()) {
                        String method = String.valueOf(methodEntry.getKey()).toUpperCase();
                        if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
                            continue;
                        }
                        String name = path;
                        if (methodEntry.getValue() instanceof Map<?, ?> op
                                && op.get("summary") != null) {
                            name = String.valueOf(op.get("summary"));
                        }
                        ApiDefinitionEntity api = new ApiDefinitionEntity();
                        api.setProjectId(projectId);
                        api.setName(name);
                        api.setMethod(method);
                        api.setPath(path);
                        api.setHeaders("[]");
                        api.setQuery("[]");
                        api.setTags("[]");
                        apiMapper.insert(api);
                        count++;
                    }
                }
            }
        }
        return Result.ok(count);
    }
}
