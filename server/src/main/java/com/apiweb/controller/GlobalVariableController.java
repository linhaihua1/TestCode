package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.entity.GlobalVariableEntity;
import com.apiweb.mapper.GlobalVariableMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 全局变量管理。
 */
@RestController
@RequestMapping("/api/v1/global-variables")
@RequiredArgsConstructor
public class GlobalVariableController {

    private final GlobalVariableMapper globalVariableMapper;

    @GetMapping
    public Result<List<GlobalVariableEntity>> list(@RequestParam String projectId) {
        return Result.ok(globalVariableMapper.selectList(
                new LambdaQueryWrapper<GlobalVariableEntity>()
                        .eq(GlobalVariableEntity::getProjectId, projectId)
                        .orderByAsc(GlobalVariableEntity::getName)));
    }

    @AuditLog(action = "create", entityType = "global_variable")
    @PostMapping
    public Result<GlobalVariableEntity> create(@RequestBody GlobalVariableEntity variable) {
        if (variable.getValue() == null) {
            variable.setValue("");
        }
        globalVariableMapper.insert(variable);
        return Result.ok(variable);
    }

    @AuditLog(action = "update", entityType = "global_variable")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody GlobalVariableEntity variable) {
        variable.setId(id);
        globalVariableMapper.updateById(variable);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "global_variable")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        globalVariableMapper.deleteById(id);
        return Result.ok();
    }
}
