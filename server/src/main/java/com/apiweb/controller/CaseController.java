package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.casee.CaseReq;
import com.apiweb.dto.casee.CaseReviewReq;
import com.apiweb.service.CaseService;
import com.apiweb.vo.casee.CaseReviewVO;
import com.apiweb.vo.casee.CaseVO;
import com.apiweb.vo.casee.CaseVersionVO;
import com.apiweb.vo.casee.DebugRecordVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用例管理：CRUD + 版本历史 + 评审流 + 调试执行 + 调试记录 + 回收站。
 */
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    // ---------------- CRUD ----------------

    /**
     * 分页查询用例列表。
     */
    @GetMapping
    public Result<List<CaseVO>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        return Result.ok(caseService.list(projectId, moduleId, status, keyword, page, size));
    }

    /**
     * 获取单个用例详情。
     */
    @GetMapping("/{id}")
    public Result<CaseVO> get(@PathVariable String id) {
        return Result.ok(caseService.get(id));
    }

    /**
     * 新建用例。
     *
     * @audit 写审计日志：action=create, entityType=case
     */
    @AuditLog(action = "create", entityType = "case")
    @PostMapping
    public Result<CaseVO> create(@Valid @RequestBody CaseReq req) {
        return Result.ok(caseService.create(req));
    }

    /**
     * 更新用例。
     *
     * @audit 写审计日志：action=update, entityType=case
     */
    @AuditLog(action = "update", entityType = "case")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody CaseReq req) {
        caseService.update(id, req);
        return Result.ok();
    }

    /**
     * 软删除用例到回收站。
     *
     * @audit 写审计日志：action=delete, entityType=case
     */
    @AuditLog(action = "delete", entityType = "case")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        caseService.delete(id);
        return Result.ok();
    }

    // ---------------- 版本历史 ----------------

    /**
     * 获取用例的版本历史列表。
     */
    @GetMapping("/{id}/versions")
    public Result<List<CaseVersionVO>> versions(@PathVariable String id) {
        return Result.ok(caseService.versions(id));
    }

    /**
     * 从指定历史版本恢复用例。
     */
    @PostMapping("/{id}/versions/{version}/restore")
    public Result<Void> restoreVersion(@PathVariable String id, @PathVariable Integer version) {
        caseService.restoreVersion(id, version);
        return Result.ok();
    }

    // ---------------- 评审流 ----------------

    /**
     * 用例评审动作。
     */
    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable String id, @RequestBody(required = false) CaseReviewReq req) {
        caseService.review(id, req == null ? new CaseReviewReq() : req);
        return Result.ok();
    }

    /**
     * 获取某用例的评审流水记录。
     */
    @GetMapping("/{id}/reviews")
    public Result<List<CaseReviewVO>> reviews(@PathVariable String id) {
        return Result.ok(caseService.reviews(id));
    }

    // ---------------- 调试执行 ----------------

    /**
     * 用例调试。
     *
     * @audit 写审计日志：action=debug, entityType=case
     */
    @AuditLog(action = "debug", entityType = "case")
    @PostMapping("/{id}/debug")
    public Result<DebugRecordVO> debug(@PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        String environmentId = body == null ? null
                : (body.get("environmentId") == null ? null : String.valueOf(body.get("environmentId")));
        return Result.ok(caseService.debug(id, environmentId));
    }

    /**
     * 全局调试记录：按项目聚合全部用例的调试记录（文档 §2.2 全局视图）。
     */
    @GetMapping("/debug-records")
    public Result<List<DebugRecordVO>> debugRecordsByProject(@RequestParam String projectId,
                                                             @RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "20") long size) {
        return Result.ok(caseService.debugRecordsByProject(projectId, page, size));
    }

    /**
     * 分页查询用例的调试记录列表。
     */
    @GetMapping("/{id}/debug-records")
    public Result<List<DebugRecordVO>> debugRecords(@PathVariable String id,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return Result.ok(caseService.debugRecords(id, page, size));
    }

    // ---------------- 回收站 ----------------

    /**
     * 查询回收站中指定项目的已软删除用例。
     */
    @GetMapping("/recycle-bin")
    public Result<List<CaseVO>> recycleBin(@RequestParam String projectId) {
        return Result.ok(caseService.recycleBin(projectId));
    }

    /**
     * 从回收站恢复用例。
     */
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable String id) {
        caseService.restore(id);
        return Result.ok();
    }

    /**
     * 物理删除回收站中的用例。
     */
    @DeleteMapping("/{id}/purge")
    public Result<Void> purge(@PathVariable String id) {
        caseService.purge(id);
        return Result.ok();
    }
}
