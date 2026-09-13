package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.ModuleEntity;
import com.apiweb.mapper.ModuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模块树管理：多级模块树（用例库/接口库的目录结构）。
 *
 * <p>删除采用"软删除到回收站"语义：被删除的模块及子模块设置 {@code deleted_at}，
 * 通过 {@link com.apiweb.controller.RecycleBinController} 还原或永久删除。
 */
@RestController
@RequestMapping("/api/v1/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleMapper moduleMapper;

    /** 树节点视图 */
    @Data
    public static class ModuleNode {
        private String key;
        private String title;
        private String parentId;
        private String type;
        private Integer sortOrder;
        private List<ModuleNode> children = new ArrayList<>();
    }

    /**
     * 获取指定项目的模块树（按 type 区分用例库/接口库等），仅返回未删除模块，组装成多级树。
     *
     * @param projectId 项目 ID（必填）
     * @param type      模块类型，默认 "case"（可选，如 case/api 等）
     * @return 多级模块树（children 嵌套）；节点 key=id, title=name
     */
    @GetMapping("/tree")
    public Result<List<ModuleNode>> tree(@RequestParam String projectId,
                                         @RequestParam(defaultValue = "case") String type) {
        List<ModuleEntity> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<ModuleEntity>()
                        .eq(ModuleEntity::getProjectId, projectId)
                        .eq(ModuleEntity::getType, type)
                        .isNull(ModuleEntity::getDeletedAt) // 过滤已软删除到回收站的模块
                        .orderByAsc(ModuleEntity::getSortOrder));
        Map<String, List<ModuleEntity>> byParent = modules.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? "" : m.getParentId()));
        return Result.ok(buildTree("", byParent));
    }

    private List<ModuleNode> buildTree(String parentId, Map<String, List<ModuleEntity>> byParent) {
        List<ModuleNode> nodes = new ArrayList<>();
        for (ModuleEntity m : byParent.getOrDefault(parentId, List.of())) {
            ModuleNode node = new ModuleNode();
            node.setKey(m.getId());
            node.setTitle(m.getName());
            node.setParentId(m.getParentId());
            node.setType(m.getType());
            node.setSortOrder(m.getSortOrder());
            node.setChildren(buildTree(m.getId(), byParent));
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 新建模块：补齐 type（默认 case）/sortOrder（默认 0）默认值后落库。
     *
     * @param module 模块实体（请求体），name 必填
     * @return 已落库的模块实体（含生成 ID）
     * @throws BizException 模块名为空时抛 badRequest
     * @audit 写审计日志：action=create, entityType=module
     */
    @AuditLog(action = "create", entityType = "module")
    @PostMapping
    public Result<ModuleEntity> create(@RequestBody ModuleEntity module) {
        if (module.getName() == null || module.getName().isBlank()) {
            throw BizException.badRequest("模块名称必填");
        }
        if (module.getType() == null) {
            module.setType("case");
        }
        if (module.getSortOrder() == null) {
            module.setSortOrder(0);
        }
        moduleMapper.insert(module);
        return Result.ok(module);
    }

    /**
     * 更新模块（覆盖请求体可写字段，不改动 parentId 等归属逻辑由前端控制）。
     *
     * @param id     模块 ID（路径参数，必填）
     * @param module 模块实体（请求体）
     * @return 无业务数据
     * @throws BizException 模块不存在时抛 notFound
     * @audit 写审计日志：action=update, entityType=module
     */
    @AuditLog(action = "update", entityType = "module")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody ModuleEntity module) {
        if (moduleMapper.selectById(id) == null) {
            throw BizException.notFound("模块不存在");
        }
        module.setId(id);
        moduleMapper.updateById(module);
        return Result.ok();
    }

    /**
     * 删除模块：级联软删除所有子模块到回收站。
     *
     * <p>需求文档 §2.3：删除有子目录时，先级联标记所有子目录的 {@code deleted_at}，
     * 由用户在回收站二次确认永久删除或还原。
     */
    @AuditLog(action = "delete", entityType = "module")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        List<String> toDelete = new ArrayList<>();
        collectSubtree(id, toDelete);
        Instant now = Instant.now();
        for (String mid : toDelete) {
            ModuleEntity m = moduleMapper.selectById(mid);
            if (m != null) {
                m.setDeletedAt(now);
                moduleMapper.updateById(m);
            }
        }
        return Result.ok();
    }

    private void collectSubtree(String moduleId, List<String> acc) {
        // 递归收集整棵子树：先加入自身，再对直接子节点继续向下收集
        acc.add(moduleId);
        List<ModuleEntity> children = moduleMapper.selectList(
                new LambdaQueryWrapper<ModuleEntity>().eq(ModuleEntity::getParentId, moduleId));
        for (ModuleEntity child : children) {
            collectSubtree(child.getId(), acc);
        }
    }
}
