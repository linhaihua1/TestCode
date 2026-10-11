package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.project.ProjectReq;
import com.apiweb.service.ProjectService;
import com.apiweb.vo.project.ProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目管理。
 *
 * <p>企业级分层：本 Controller 只负责参数接收 + 调 Service + 返回 VO，
 * 不再直接注入 Mapper。</p>
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * 查询项目列表。
     */
    @GetMapping
    public Result<List<ProjectVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(projectService.list(keyword));
    }

    /**
     * 获取项目详情。
     */
    @GetMapping("/{id}")
    public Result<ProjectVO> get(@PathVariable String id) {
        return Result.ok(projectService.get(id));
    }

    /**
     * 新建项目。
     */
    @AuditLog(action = "create", entityType = "project")
    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectReq req) {
        return Result.ok(projectService.create(req));
    }

    /**
     * 更新项目。
     */
    @AuditLog(action = "update", entityType = "project")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody ProjectReq req) {
        projectService.update(id, req);
        return Result.ok();
    }

    /**
     * 删除项目。
     */
    @AuditLog(action = "delete", entityType = "project")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        projectService.delete(id);
        return Result.ok();
    }
}
