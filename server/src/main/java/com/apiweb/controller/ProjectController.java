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
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;

    /**
     * 查询项目列表（可按名称关键字模糊搜索，按更新时间倒序）。
     *
     * @param keyword 关键字（可选），按项目名称模糊匹配
     * @return 项目列表
     */
    @GetMapping
    public Result<List<ProjectEntity>> list(@RequestParam(required = false) String keyword) {
        List<ProjectEntity> projects = projectMapper.selectList(
                new LambdaQueryWrapper<ProjectEntity>()
                        .like(keyword != null && !keyword.isBlank(),
                                ProjectEntity::getName, keyword)
                        .orderByDesc(ProjectEntity::getUpdatedAt));
        return Result.ok(projects);
    }

    /**
     * 获取项目详情。
     *
     * @param id 项目 ID（路径参数，必填）
     * @return 项目实体
     * @throws BizException 项目不存在时抛 notFound
     */
    @GetMapping("/{id}")
    public Result<ProjectEntity> get(@PathVariable String id) {
        ProjectEntity project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        return Result.ok(project);
    }

    /**
     * 新建项目。
     *
     * @param project 项目实体（请求体），name 必填
     * @return 已落库的项目实体
     * @throws BizException 项目名为空时抛 badRequest
     * @audit 写审计日志：action=create, entityType=project
     */
    @AuditLog(action = "create", entityType = "project")
    @PostMapping
    public Result<ProjectEntity> create(@RequestBody ProjectEntity project) {
        if (project.getName() == null || project.getName().isBlank()) {
            throw BizException.badRequest("项目名称必填");
        }
        projectMapper.insert(project);
        return Result.ok(project);
    }

    /**
     * 更新项目（覆盖请求体可写字段）。
     *
     * @param id      项目 ID（路径参数，必填）
     * @param project 项目实体（请求体）
     * @return 无业务数据
     * @throws BizException 项目不存在时抛 notFound
     * @audit 写审计日志：action=update, entityType=project
     */
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

    /**
     * 删除项目（物理删除，调用方需确保已清理其下用例/接口等数据）。
     *
     * @param id 项目 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=project
     */
    @AuditLog(action = "delete", entityType = "project")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        projectMapper.deleteById(id);
        return Result.ok();
    }
}
