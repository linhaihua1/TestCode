package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.ProjectEntity;
import com.apiweb.mapper.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目管理。
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;

    @GetMapping
    public Result<List<ProjectEntity>> list(@RequestParam(required = false) String keyword) {
        List<ProjectEntity> projects = projectMapper.selectList(
                new LambdaQueryWrapper<ProjectEntity>()
                        .like(keyword != null && !keyword.isBlank(),
                                ProjectEntity::getName, keyword)
                        .orderByDesc(ProjectEntity::getUpdatedAt));
        return Result.ok(projects);
    }

    @GetMapping("/{id}")
    public Result<ProjectEntity> get(@PathVariable String id) {
        ProjectEntity project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        return Result.ok(project);
    }

    @AuditLog(action = "create", entityType = "project")
    @PostMapping
    public Result<ProjectEntity> create(@RequestBody ProjectEntity project) {
        if (project.getName() == null || project.getName().isBlank()) {
            throw BizException.badRequest("项目名称必填");
        }
        projectMapper.insert(project);
        return Result.ok(project);
    }

    @AuditLog(action = "update", entityType = "project")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody ProjectEntity project) {
        if (projectMapper.selectById(id) == null) {
            throw BizException.notFound("项目不存在");
        }
        project.setId(id);
        projectMapper.updateById(project);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "project")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        projectMapper.deleteById(id);
        return Result.ok();
    }
}
