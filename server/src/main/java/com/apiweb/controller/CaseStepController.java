package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.mapper.CaseStepMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用例步骤管理（按需求文档 §4.2 步骤 CRUD + 嵌套 + 排序）。
 *
 * <h3>API</h3>
 * <ul>
 *   <li>GET    /api/v1/case-steps?caseId=xxx — 列出用例全部步骤（扁平）</li>
 *   <li>POST   /api/v1/case-steps — 新增步骤</li>
 *   <li>PUT    /api/v1/case-steps/{id} — 更新步骤</li>
 *   <li>DELETE /api/v1/case-steps/{id} — 删除步骤（含子步骤）</li>
 *   <li>POST   /api/v1/case-steps/save-batch — 批量保存（前端拖拽排序场景）</li>
 *   <li>POST   /api/v1/case-steps/reorder — 调整顺序</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/case-steps")
@RequiredArgsConstructor
public class CaseStepController {

    private final CaseStepMapper caseStepMapper;

    /**
     * 列出某用例的全部步骤（扁平列表，按 position 与 sortOrder 升序）。
     *
     * @param caseId 用例 ID（必填，query 参数）
     * @return 该用例下的步骤列表（含父子信息，由前端自行组装树）
     */
    @GetMapping
    public Result<List<CaseStepEntity>> list(@RequestParam String caseId) {
        return Result.ok(caseStepMapper.selectList(
                new LambdaQueryWrapper<CaseStepEntity>()
                        .eq(CaseStepEntity::getCaseId, caseId)
                        .orderByAsc(CaseStepEntity::getPosition, CaseStepEntity::getSortOrder)));
    }

    /**
     * 新增单个步骤：补齐排序/位置/启用/失败策略/config 等默认值后落库。
     *
     * @param step 步骤实体（请求体），caseId/stepType/name 为必填（详见 validate）
     * @return 已落库的完整步骤实体（含生成的 ID）
     * @throws BizException 必填字段缺失时抛 badRequest
     * @audit 写审计日志：action=create, entityType=case_step
     */
    @AuditLog(action = "create", entityType = "case_step")
    @PostMapping
    public Result<CaseStepEntity> create(@RequestBody CaseStepEntity step) {
        validate(step);
        if (step.getSortOrder() == null) step.setSortOrder(0);
        if (step.getPosition() == null || step.getPosition().isBlank()) step.setPosition("TEST");
        if (step.getEnabled() == null) step.setEnabled(true);
        if (step.getFailStrategy() == null || step.getFailStrategy().isBlank()) step.setFailStrategy("stop");
        if (step.getConfig() == null || step.getConfig().isBlank()) step.setConfig("{}");
        caseStepMapper.insert(step);
        return Result.ok(step);
    }

    /**
     * 更新单个步骤（覆盖请求体中的可写字段）。
     *
     * @param id   步骤 ID（路径参数，必填）
     * @param step 步骤实体（请求体）
     * @return 无业务数据
     * @throws BizException 步骤不存在时抛 notFound；必填字段缺失抛 badRequest
     * @audit 写审计日志：action=update, entityType=case_step
     */
    @AuditLog(action = "update", entityType = "case_step")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody CaseStepEntity step) {
        CaseStepEntity existing = caseStepMapper.selectById(id);
        if (existing == null) throw BizException.notFound("步骤不存在");
        validate(step);
        step.setId(id);
        caseStepMapper.updateById(step);
        return Result.ok();
    }

    /**
     * 删除步骤，并级联删除其所有子步骤（递归收集整棵子树后逐个物理删除）。
     *
     * @param id 步骤 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=case_step
     */
    @AuditLog(action = "delete", entityType = "case_step")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 级联删除所有子步骤
        List<String> ids = new ArrayList<>();
        collectSubtree(id, ids);
        ids.forEach(caseStepMapper::deleteById);
        return Result.ok();
    }

    /**
     * 批量保存（前端拖拽后整体提交：清空旧步骤 + 重插新步骤含父子关系）。
     *
     * @param req 请求体，含 caseId（必填）与 steps（步骤数组，已含父子关系）
     * @return 无业务数据
     * @throws BizException caseId 为空时抛 badRequest
     * @audit 写审计日志：action=save_batch, entityType=case_step
     */
    @AuditLog(action = "save_batch", entityType = "case_step")
    @PostMapping("/save-batch")
    public Result<Void> saveBatch(@RequestBody BatchSaveRequest req) {
        if (req == null || req.getCaseId() == null || req.getCaseId().isBlank()) {
            throw BizException.badRequest("caseId 必填");
        }
        // 1. 清理当前用例的全部步骤
        caseStepMapper.delete(
                new LambdaQueryWrapper<CaseStepEntity>().eq(CaseStepEntity::getCaseId, req.getCaseId()));
        // 2. 重新插入（已包含父子关系）
        if (req.getSteps() != null) {
            for (CaseStepEntity s : req.getSteps()) {
                s.setId(null);
                s.setCaseId(req.getCaseId());
                if (s.getConfig() == null || s.getConfig().isBlank()) s.setConfig("{}");
                if (s.getFailStrategy() == null || s.getFailStrategy().isBlank()) s.setFailStrategy("stop");
                if (s.getEnabled() == null) s.setEnabled(true);
                if (s.getPosition() == null || s.getPosition().isBlank()) s.setPosition("TEST");
                caseStepMapper.insert(s);
            }
        }
        return Result.ok();
    }

    /**
     * 调整步骤顺序：按传入 stepIds 的顺序重置同级步骤的 sortOrder（全量覆盖式排序）。
     *
     * @param req 请求体，含 parentId（可选）与 stepIds（目标顺序，必填且非空）
     * @return 无业务数据
     * @throws BizException stepIds 为空时抛 badRequest
     */
    @PostMapping("/reorder")
    public Result<Void> reorder(@RequestBody ReorderRequest req) {
        if (req == null || req.getStepIds() == null || req.getStepIds().isEmpty()) {
            throw BizException.badRequest("stepIds 必填");
        }
        for (int i = 0; i < req.getStepIds().size(); i++) {
            CaseStepEntity s = new CaseStepEntity();
            s.setId(req.getStepIds().get(i));
            s.setSortOrder(i);
            caseStepMapper.updateById(s);
        }
        return Result.ok();
    }

    private void validate(CaseStepEntity step) {
        if (step.getCaseId() == null || step.getCaseId().isBlank()) {
            throw BizException.badRequest("caseId 必填");
        }
        if (step.getStepType() == null || step.getStepType().isBlank()) {
            throw BizException.badRequest("stepType 必填");
        }
        if (step.getName() == null || step.getName().isBlank()) {
            throw BizException.badRequest("步骤名称必填");
        }
    }

    private void collectSubtree(String rootId, List<String> acc) {
        // 递归收集整棵子树：先加入自身，再对直接子节点继续向下收集
        acc.add(rootId);
        List<CaseStepEntity> children = caseStepMapper.selectList(
                new LambdaQueryWrapper<CaseStepEntity>().eq(CaseStepEntity::getParentId, rootId));
        for (CaseStepEntity c : children) {
            collectSubtree(c.getId(), acc);
        }
    }

    @Data
    public static class BatchSaveRequest {
        private String caseId;
        private List<CaseStepEntity> steps;
    }

    @Data
    public static class ReorderRequest {
        private String parentId;
        private List<String> stepIds;
    }
}