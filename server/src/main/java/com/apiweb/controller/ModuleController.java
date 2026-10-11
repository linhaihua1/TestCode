package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.module.ModuleReq;
import com.apiweb.service.ModuleService;
import com.apiweb.vo.module.ModuleNodeVO;
import com.apiweb.vo.module.ModuleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模块树管理：多级模块树（用例库/接口库的目录结构）。
 *
 * <p>删除采用"软删除到回收站"语义：被删除的模块及子模块设置 {@code deleted_at}，
 * 通过回收站还原或永久删除。
 */
@RestController
@RequestMapping("/api/v1/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    /**
     * 获取指定项目的模块树（按 type 区分用例库/接口库等），仅返回未删除模块，组装成多级树。
     *
     * @param projectId 项目 ID（必填）
     * @param type      模块类型，默认 "case"（可选，如 case/api 等）
     * @return 多级模块树（children 嵌套）；节点 key=id, title=name
     */
    @GetMapping("/tree")
    public Result<List<ModuleNodeVO>> tree(@RequestParam String projectId,
                                           @RequestParam(defaultValue = "case") String type) {
        return Result.ok(moduleService.tree(projectId, type));
    }

    /**
     * 新建模块：补齐 type（默认 case）/sortOrder（默认 0）默认值后落库。
     *
     * @param req 模块请求 DTO（请求体），name 必填
     * @return 已落库的模块 VO（含生成 ID）
     * @audit 写审计日志：action=create, entityType=module
     */
    @AuditLog(action = "create", entityType = "module")
    @PostMapping
    public Result<ModuleVO> create(@Valid @RequestBody ModuleReq req) {
        return Result.ok(moduleService.create(req));
    }

    /**
     * 更新模块（覆盖请求体可写字段）。
     *
     * @param id  模块 ID（路径参数，必填）
     * @param req 模块请求 DTO（请求体）
     * @return 无业务数据
     * @audit 写审计日志：action=update, entityType=module
     */
    @AuditLog(action = "update", entityType = "module")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody ModuleReq req) {
        moduleService.update(id, req);
        return Result.ok();
    }

    /**
     * 删除模块：级联软删除所有子模块到回收站。
     *
     * @param id 模块 ID（路径参数，必填）
     * @return 无业务数据
     */
    @AuditLog(action = "delete", entityType = "module")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        moduleService.delete(id);
        return Result.ok();
    }
}
