package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.ModuleConverter;
import com.apiweb.dto.module.ModuleReq;
import com.apiweb.entity.ModuleEntity;
import com.apiweb.mapper.ModuleMapper;
import com.apiweb.vo.module.ModuleNodeVO;
import com.apiweb.vo.module.ModuleVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模块树业务服务：多级模块树的查询与维护。
 *
 * <p>删除采用"软删除到回收站"语义：被删除的模块及子模块设置 {@code deleted_at}，
 * 通过回收站还原或永久删除。</p>
 */
@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleMapper moduleMapper;
    private final ModuleConverter moduleConverter;

    /**
     * 获取指定项目的模块树（按 type 区分），仅返回未删除模块，组装成多级树。
     */
    public List<ModuleNodeVO> tree(String projectId, String type) {
        List<ModuleEntity> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<ModuleEntity>()
                        .eq(ModuleEntity::getProjectId, projectId)
                        .eq(ModuleEntity::getType, type)
                        .isNull(ModuleEntity::getDeletedAt)
                        .orderByAsc(ModuleEntity::getSortOrder));
        Map<String, List<ModuleEntity>> byParent = modules.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? "" : m.getParentId()));
        return buildTree("", byParent);
    }

    private List<ModuleNodeVO> buildTree(String parentId, Map<String, List<ModuleEntity>> byParent) {
        List<ModuleNodeVO> nodes = new ArrayList<>();
        for (ModuleEntity m : byParent.getOrDefault(parentId, List.of())) {
            ModuleNodeVO node = new ModuleNodeVO();
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
     * 新建模块：补齐 type（默认 case）/sortOrder（默认 0）后落库。
     */
    @Transactional
    public ModuleVO create(ModuleReq req) {
        ModuleEntity module = new ModuleEntity();
        module.setProjectId(req.getProjectId());
        module.setParentId(req.getParentId());
        module.setName(req.getName());
        module.setType(req.getType() == null ? "case" : req.getType());
        module.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        moduleMapper.insert(module);
        return moduleConverter.toVO(module);
    }

    /**
     * 更新模块（覆盖请求体可写字段）。
     */
    @Transactional
    public void update(String id, ModuleReq req) {
        if (moduleMapper.selectById(id) == null) {
            throw BizException.notFound("模块不存在");
        }
        ModuleEntity module = new ModuleEntity();
        module.setId(id);
        module.setProjectId(req.getProjectId());
        module.setParentId(req.getParentId());
        module.setName(req.getName());
        module.setType(req.getType());
        module.setSortOrder(req.getSortOrder());
        moduleMapper.updateById(module);
    }

    /**
     * 删除模块：级联软删除所有子模块到回收站。
     */
    @Transactional
    public void delete(String id) {
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
    }

    private void collectSubtree(String moduleId, List<String> acc) {
        acc.add(moduleId);
        List<ModuleEntity> children = moduleMapper.selectList(
                new LambdaQueryWrapper<ModuleEntity>().eq(ModuleEntity::getParentId, moduleId));
        for (ModuleEntity child : children) {
            collectSubtree(child.getId(), acc);
        }
    }
}
