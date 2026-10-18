package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.CaseStepConverter;
import com.apiweb.dto.casestep.CaseStepReq;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.CaseStepMapper;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.casestep.CaseStepVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用例步骤业务服务。
 *
 * <h3>步骤存储的双写同源约定（重要）</h3>
 * <p>步骤有两份等价表示：</p>
 * <ul>
 *   <li>{@code t_case.steps}（JSON 数组）—— <b>执行真源</b>。调试执行（{@code CaseService.debug}）
 *       与任务执行（{@code ApiTaskConsumer}）都直接把它交给 {@code CaseRunner} 运行。</li>
 *   <li>{@code t_case_step}（表）—— 规范化投影，供步骤 CRUD API 与引用公共用例
 *       （{@code RefPublicCaseExecutor} → {@code CaseRunner.loadCaseSteps}）使用。</li>
 * </ul>
 * <p>为避免二者漂移（曾导致「一键生成用例 / 批量保存步骤后调试跑 0 个步骤」），
 * 本服务的<b>任何</b>写操作都会在事务内调用 {@link #syncCaseStepsJson(String)} 把表回写为 JSON；
 * 反方向由 {@link #replaceStepsFromJson(String, String)} 完成，且保证无损：步骤类型相关的字段
 * 原样落进 {@code config}，身份字段（type/name/position/...）单独成列。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseStepService {

    /**
     * 步骤「身份字段」——单独成列，其余字段全部原样进 config，保证双向无损。
     *
     * <p>注意：某些步骤类型的 config 会用到与身份字段同名的键（如 {@code VARIABLE_ASSIGN}
     * 用 {@code name} 表示变量名）。这些键在回写扁平 JSON 时必须落进 {@code config} 子对象，
     * 否则会覆盖步骤自身的 {@code name}，并导致执行器读不到变量名。</p>
     */
    private static final Set<String> IDENTITY_KEYS = Set.of(
            "id", "_id", "caseId", "parentId", "sortOrder",
            "type", "name", "position", "enabled", "failStrategy", "remark", "children", "config");

    /** 步骤分区执行顺序权重：PRE → TEST → POST（与前端分区一致，避免回写时乱序执行）。 */
    private static final Map<String, Integer> POSITION_RANK = Map.of("PRE", 0, "TEST", 1, "POST", 2);

    private final CaseStepMapper caseStepMapper;
    private final CaseStepConverter caseStepConverter;
    /** 用于把规范化步骤回写执行真源 {@code t_case.steps} */
    private final CaseMapper caseMapper;

    /**
     * 列出某用例的全部步骤（扁平列表，按 position 与 sortOrder 升序）。
     */
    public List<CaseStepVO> list(String caseId) {
        return caseStepConverter.toVOList(caseStepMapper.selectList(
                new LambdaQueryWrapper<CaseStepEntity>()
                        .eq(CaseStepEntity::getCaseId, caseId)
                        .orderByAsc(CaseStepEntity::getPosition, CaseStepEntity::getSortOrder)));
    }

    /**
     * 新增单个步骤：补齐默认值后落库，并回写执行真源。
     */
    @Transactional
    public CaseStepVO create(CaseStepReq req) {
        CaseStepEntity step = caseStepConverter.toEntity(req);
        applyDefaults(step);
        caseStepMapper.insert(step);
        syncCaseStepsJson(step.getCaseId());
        return caseStepConverter.toVO(step);
    }

    /**
     * 更新单个步骤，并回写执行真源。
     */
    @Transactional
    public void update(String id, CaseStepReq req) {
        CaseStepEntity existing = caseStepMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("步骤不存在");
        }
        CaseStepEntity step = caseStepConverter.toEntity(req);
        step.setId(id);
        caseStepMapper.updateById(step);
        syncCaseStepsJson(existing.getCaseId());
    }

    /**
     * 删除步骤，级联删除其所有子步骤，并回写执行真源。
     */
    @Transactional
    public void delete(String id) {
        CaseStepEntity root = caseStepMapper.selectById(id);
        List<String> ids = new ArrayList<>();
        collectSubtree(id, ids);
        ids.forEach(caseStepMapper::deleteById);
        if (root != null) {
            syncCaseStepsJson(root.getCaseId());
        }
    }

    /**
     * 批量保存（前端拖拽后整体提交：清空旧步骤 + 重插新步骤含父子关系），并回写执行真源。
     */
    @Transactional
    public void saveBatch(String caseId, List<CaseStepReq> steps) {
        if (caseId == null || caseId.isBlank()) {
            throw BizException.badRequest("caseId 必填");
        }
        caseStepMapper.delete(
                new LambdaQueryWrapper<CaseStepEntity>().eq(CaseStepEntity::getCaseId, caseId));
        if (steps != null) {
            for (CaseStepReq r : steps) {
                CaseStepEntity s = caseStepConverter.toEntity(r);
                s.setId(null);
                s.setCaseId(caseId);
                applyDefaults(s);
                caseStepMapper.insert(s);
            }
        }
        syncCaseStepsJson(caseId);
    }

    /**
     * 调整步骤顺序：按传入 stepIds 顺序重置 sortOrder，并回写执行真源。
     */
    @Transactional
    public void reorder(List<String> stepIds) {
        if (stepIds == null || stepIds.isEmpty()) {
            throw BizException.badRequest("stepIds 必填");
        }
        String caseId = null;
        for (int i = 0; i < stepIds.size(); i++) {
            CaseStepEntity s = new CaseStepEntity();
            s.setId(stepIds.get(i));
            s.setSortOrder(i);
            caseStepMapper.updateById(s);
            if (caseId == null) {
                CaseStepEntity probe = caseStepMapper.selectById(stepIds.get(i));
                if (probe != null) {
                    caseId = probe.getCaseId();
                }
            }
        }
        if (caseId != null) {
            syncCaseStepsJson(caseId);
        }
    }

    // ============================================================
    // 执行真源（t_case.steps JSON）与步骤表（t_case_step）的双向同步
    // ============================================================

    /**
     * 用扁平步骤 JSON 全量重建步骤表（无损：非身份字段原样作为 config，支持 children 嵌套）。
     *
     * <p>供「用例保存」与「一键生成用例」使用，使步骤表与执行真源同源。</p>
     *
     * @param caseId    用例 ID
     * @param stepsJson 扁平步骤 JSON 数组（前端 {@code CaseInfo.steps} 的结构）
     */
    @Transactional
    public void replaceStepsFromJson(String caseId, String stepsJson) {
        if (caseId == null || caseId.isBlank()) {
            return;
        }
        // null = 请求未携带 steps（如仅改名称/标签的部分更新）→ 保持既有步骤，绝不能清空
        if (stepsJson == null) {
            return;
        }
        caseStepMapper.delete(new LambdaQueryWrapper<CaseStepEntity>().eq(CaseStepEntity::getCaseId, caseId));
        if (stepsJson.isBlank()) {
            return;
        }
        List<Map<String, Object>> steps;
        try {
            steps = JsonUtils.toList(stepsJson);
        } catch (Exception e) {
            return;
        }
        if (steps == null || steps.isEmpty()) {
            return;
        }
        insertFlatSteps(caseId, null, steps, new int[]{0});
    }

    /**
     * 把步骤表回写为执行真源 {@code t_case.steps}（保持 PRE → TEST → POST 执行顺序）。
     *
     * <p>步骤 CRUD / 批量保存 / 排序后的必经步骤，保证调试与任务执行能读到最新步骤。</p>
     */
    @Transactional
    public void syncCaseStepsJson(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return;
        }
        List<CaseStepEntity> ordered = new ArrayList<>(caseStepMapper.selectList(
                new LambdaQueryWrapper<CaseStepEntity>().eq(CaseStepEntity::getCaseId, caseId)));
        ordered.sort(stepOrder());

        Map<String, Map<String, Object>> flatById = new LinkedHashMap<>();
        Map<String, String> parentById = new LinkedHashMap<>();
        for (CaseStepEntity e : ordered) {
            flatById.put(e.getId(), entityToFlat(e));
            parentById.put(e.getId(), e.getParentId());
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (CaseStepEntity e : ordered) {
            Map<String, Object> flat = flatById.get(e.getId());
            String parentId = e.getParentId();
            Map<String, Object> parent = (parentId == null || parentId.isBlank()) ? null : flatById.get(parentId);
            // 父节点缺失、或父子互为祖先（脏数据成环）时提为根节点：
            // 否则该节点永远不会被任何根节点串起来，回写时整棵子树被静默丢弃
            if (parent == null || hasCycle(e.getId(), parentId, parentById)) {
                roots.add(flat);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.computeIfAbsent(
                        "children", k -> new ArrayList<Map<String, Object>>());
                children.add(flat);
            }
        }

        CaseEntity patch = new CaseEntity();
        patch.setId(caseId);
        patch.setSteps(JsonUtils.toJson(roots));
        caseMapper.updateById(patch);
    }

    // ============================================================
    // 内部
    // ============================================================

    private void applyDefaults(CaseStepEntity step) {
        if (step.getSortOrder() == null) step.setSortOrder(0);
        if (step.getPosition() == null || step.getPosition().isBlank()) step.setPosition("TEST");
        if (step.getEnabled() == null) step.setEnabled(true);
        if (step.getFailStrategy() == null || step.getFailStrategy().isBlank()) step.setFailStrategy("stop");
        if (step.getConfig() == null || step.getConfig().isBlank()) step.setConfig("{}");
    }

    @SuppressWarnings("unchecked")
    private void insertFlatSteps(String caseId, String parentId, List<Map<String, Object>> steps, int[] order) {
        for (Map<String, Object> raw : steps) {
            if (raw == null) {
                continue;
            }
            CaseStepEntity step = flatToEntity(caseId, parentId, raw, order[0]++);
            caseStepMapper.insert(step);
            Object children = raw.get("children");
            if (children instanceof List<?> kids && !kids.isEmpty()) {
                List<Map<String, Object>> childSteps = new ArrayList<>();
                for (Object kid : kids) {
                    if (kid instanceof Map<?, ?> m) {
                        childSteps.add((Map<String, Object>) m);
                    }
                }
                insertFlatSteps(caseId, step.getId(), childSteps, order);
            }
        }
    }

    private CaseStepEntity flatToEntity(String caseId, String parentId, Map<String, Object> raw, int sortOrder) {
        CaseStepEntity step = new CaseStepEntity();
        step.setCaseId(caseId);
        step.setParentId(parentId);
        String type = asString(raw.get("type"));
        step.setStepType(type == null || type.isBlank() ? "HTTP_REQUEST" : type);
        String name = asString(raw.get("name"));
        step.setName(name == null || name.isBlank() ? "步骤 " + (sortOrder + 1) : name);
        String position = asString(raw.get("position"));
        step.setPosition(position == null || position.isBlank() ? "TEST" : position);
        step.setSortOrder(sortOrder);
        step.setEnabled(!(raw.get("enabled") instanceof Boolean b) || b);
        String failStrategy = asString(raw.get("failStrategy"));
        step.setFailStrategy(failStrategy == null || failStrategy.isBlank() ? "stop" : failStrategy);
        step.setRemark(asString(raw.get("remark")));

        Map<String, Object> config = new LinkedHashMap<>();
        // 嵌套 config（部分步骤类型把变量名等冲突字段放在这里）优先
        Object nestedConfig = raw.get("config");
        if (nestedConfig instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> asMap = (Map<String, Object>) m;
            config.putAll(asMap);
        } else if (nestedConfig instanceof String s && !s.isBlank()) {
            try {
                Map<String, Object> parsed = JsonUtils.toMap(s);
                if (parsed != null) {
                    config.putAll(parsed);
                }
            } catch (Exception ignored) {
                // 非法 config 忽略，继续用扁平字段兜底
            }
        }
        raw.forEach((k, v) -> {
            if (!IDENTITY_KEYS.contains(k)) {
                config.putIfAbsent(k, v);
            }
        });
        step.setConfig(JsonUtils.toJson(config));
        return step;
    }

    private Map<String, Object> entityToFlat(CaseStepEntity e) {
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("type", e.getStepType());
        flat.put("name", e.getName());
        flat.put("position", e.getPosition());
        flat.put("enabled", e.getEnabled() == null || e.getEnabled());
        flat.put("failStrategy", e.getFailStrategy());
        if (e.getRemark() != null && !e.getRemark().isBlank()) {
            flat.put("remark", e.getRemark());
        }
        Map<String, Object> nested = new LinkedHashMap<>();
        if (e.getConfig() != null && !e.getConfig().isBlank()) {
            try {
                Map<String, Object> config = JsonUtils.toMap(e.getConfig());
                if (config != null) {
                    config.forEach((key, value) -> {
                        if (IDENTITY_KEYS.contains(key)) {
                            // 与身份字段同名（如 VARIABLE_ASSIGN 的变量名 name）→ 放进 config 子对象
                            nested.put(key, value);
                        } else {
                            flat.put(key, value);
                        }
                    });
                }
            } catch (Exception ignored) {
                // config 非法时仅保留身份字段，避免影响整体回写
            }
        }
        if (!nested.isEmpty()) {
            flat.put("config", nested);
        }
        return flat;
    }

    private Comparator<CaseStepEntity> stepOrder() {
        return Comparator
                .comparingInt((CaseStepEntity e) -> POSITION_RANK.getOrDefault(
                        e.getPosition() == null ? "TEST" : e.getPosition().toUpperCase(), 1))
                .thenComparingInt(e -> e.getSortOrder() == null ? 0 : e.getSortOrder());
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 判断把 {@code id} 挂到 {@code parentId} 下是否会形成环。
     *
     * <p>脏数据（A 的父是 B、B 的父是 A）会让节点无法被任何根节点串起来，
     * 直接挂载会导致回写 JSON 时静默丢失，故检测到环时将其提为根节点。</p>
     */
    private boolean hasCycle(String id, String parentId, Map<String, String> parentById) {
        Set<String> visited = new HashSet<>();
        String current = parentId;
        while (current != null && !current.isBlank()) {
            if (current.equals(id) || !visited.add(current)) {
                return true;
            }
            current = parentById.get(current);
        }
        return false;
    }

    private void collectSubtree(String rootId, List<String> acc) {
        collectSubtree(rootId, acc, new HashSet<>());
    }

    /** 递归收集子树；visited 防止脏数据成环导致无限递归（StackOverflowError）。 */
    private void collectSubtree(String rootId, List<String> acc, Set<String> visited) {
        if (!visited.add(rootId)) {
            return;
        }
        acc.add(rootId);
        List<CaseStepEntity> children = caseStepMapper.selectList(
                new LambdaQueryWrapper<CaseStepEntity>().eq(CaseStepEntity::getParentId, rootId));
        for (CaseStepEntity c : children) {
            collectSubtree(c.getId(), acc, visited);
        }
    }
}
