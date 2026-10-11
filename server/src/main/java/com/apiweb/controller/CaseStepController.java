package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.casestep.CaseStepBatchReq;
import com.apiweb.dto.casestep.CaseStepReorderReq;
import com.apiweb.dto.casestep.CaseStepReq;
import com.apiweb.service.CaseStepService;
import com.apiweb.vo.casestep.CaseStepVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    private final CaseStepService caseStepService;

    /**
     * 列出某用例的全部步骤（扁平列表，按 position 与 sortOrder 升序）。
     */
    @GetMapping
    public Result<List<CaseStepVO>> list(@RequestParam String caseId) {
        return Result.ok(caseStepService.list(caseId));
    }

    /**
     * 新增单个步骤。
     *
     * @param req 步骤请求 DTO（请求体），caseId/stepType/name 为必填
     * @return 已落库的完整步骤 VO（含生成的 ID）
     * @audit 写审计日志：action=create, entityType=case_step
     */
    @AuditLog(action = "create", entityType = "case_step")
    @PostMapping
    public Result<CaseStepVO> create(@Valid @RequestBody CaseStepReq req) {
        return Result.ok(caseStepService.create(req));
    }

    /**
     * 更新单个步骤。
     *
     * @param id  步骤 ID（路径参数，必填）
     * @param req 步骤请求 DTO（请求体）
     * @return 无业务数据
     * @audit 写审计日志：action=update, entityType=case_step
     */
    @AuditLog(action = "update", entityType = "case_step")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody CaseStepReq req) {
        caseStepService.update(id, req);
        return Result.ok();
    }

    /**
     * 删除步骤，并级联删除其所有子步骤。
     *
     * @param id 步骤 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=case_step
     */
    @AuditLog(action = "delete", entityType = "case_step")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        caseStepService.delete(id);
        return Result.ok();
    }

    /**
     * 批量保存（前端拖拽后整体提交）。
     *
     * @param req 请求体，含 caseId 与 steps
     * @return 无业务数据
     * @audit 写审计日志：action=save_batch, entityType=case_step
     */
    @AuditLog(action = "save_batch", entityType = "case_step")
    @PostMapping("/save-batch")
    public Result<Void> saveBatch(@RequestBody CaseStepBatchReq req) {
        caseStepService.saveBatch(req == null ? null : req.getCaseId(),
                req == null ? null : req.getSteps());
        return Result.ok();
    }

    /**
     * 调整步骤顺序。
     *
     * @param req 请求体，含 stepIds（目标顺序）
     * @return 无业务数据
     */
    @PostMapping("/reorder")
    public Result<Void> reorder(@RequestBody CaseStepReorderReq req) {
        caseStepService.reorder(req == null ? null : req.getStepIds());
        return Result.ok();
    }
}
