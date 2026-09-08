package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.VariablesResolver;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseReviewEntity;
import com.apiweb.entity.CaseVersionEntity;
import com.apiweb.entity.DebugRecordEntity;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.CaseReviewMapper;
import com.apiweb.mapper.CaseVersionMapper;
import com.apiweb.mapper.DebugRecordMapper;
import com.apiweb.security.UserContext;
import com.apiweb.service.ExecutionSupportService;
import com.apiweb.service.OssService;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用例管理：CRUD + 版本历史 + 评审流 + 调试执行 + 调试记录 + 回收站。
 */
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseMapper caseMapper;
    private final CaseVersionMapper caseVersionMapper;
    private final CaseReviewMapper caseReviewMapper;
    private final DebugRecordMapper debugRecordMapper;
    private final ExecutionSupportService executionSupport;
    private final OssService ossService;

    // ---------------- CRUD ----------------

    @GetMapping
    public Result<List<CaseEntity>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        Page<CaseEntity> result = caseMapper.selectPage(new Page<>(page, Math.min(size, 200)),
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .isNull(CaseEntity::getDeletedAt)
                        .eq(moduleId != null && !moduleId.isBlank(), CaseEntity::getModuleId, moduleId)
                        .eq(status != null && !status.isBlank(), CaseEntity::getStatus, status)
                        .like(keyword != null && !keyword.isBlank(), CaseEntity::getName, keyword)
                        .orderByAsc(CaseEntity::getSortOrder)
                        .orderByDesc(CaseEntity::getUpdatedAt));
        return Result.ok(result.getRecords());
    }

    @GetMapping("/{id}")
    public Result<CaseEntity> get(@PathVariable String id) {
        CaseEntity c = caseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("用例不存在");
        }
        return Result.ok(c);
    }

    @AuditLog(action = "create", entityType = "case")
    @PostMapping
    public Result<CaseEntity> create(@RequestBody CaseEntity c) {
        if (c.getName() == null || c.getName().isBlank()) {
            throw BizException.badRequest("用例名称必填");
        }
        if (c.getSteps() == null) {
            c.setSteps("[]");
        }
        if (c.getTags() == null) {
            c.setTags("[]");
        }
        c.setVersion(1);
        caseMapper.insert(c);
        saveVersion(c, "初始版本");
        return Result.ok(c);
    }

    @AuditLog(action = "update", entityType = "case")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody CaseEntity c) {
        CaseEntity existing = caseMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("用例不存在");
        }
        c.setId(id);
        c.setVersion(existing.getVersion() + 1);
        caseMapper.updateById(c);
        saveVersion(c, "更新");
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "case")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        CaseEntity c = caseMapper.selectById(id);
        if (c != null) {
            c.setDeletedAt(Instant.now());
            c.setStatus("trash");
            caseMapper.updateById(c);
        }
        return Result.ok();
    }

    // ---------------- 版本历史 ----------------

    @GetMapping("/{id}/versions")
    public Result<List<CaseVersionEntity>> versions(@PathVariable String id) {
        return Result.ok(caseVersionMapper.selectList(
                new LambdaQueryWrapper<CaseVersionEntity>()
                        .eq(CaseVersionEntity::getCaseId, id)
                        .orderByDesc(CaseVersionEntity::getVersion)));
    }

    @PostMapping("/{id}/versions/{version}/restore")
    public Result<Void> restoreVersion(@PathVariable String id, @PathVariable Integer version) {
        CaseEntity c = caseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("用例不存在");
        }
        CaseVersionEntity snapshot = caseVersionMapper.selectOne(
                new LambdaQueryWrapper<CaseVersionEntity>()
                        .eq(CaseVersionEntity::getCaseId, id)
                        .eq(CaseVersionEntity::getVersion, version));
        if (snapshot == null) {
            throw BizException.notFound("版本不存在");
        }
        Map<String, Object> snap = JsonUtils.toMap(snapshot.getSnapshot());
        if (snap.containsKey("steps")) {
            c.setSteps(JsonUtils.toJson(snap.get("steps")));
        }
        if (snap.containsKey("name")) {
            c.setName(String.valueOf(snap.get("name")));
        }
        c.setVersion(c.getVersion() + 1);
        caseMapper.updateById(c);
        saveVersion(c, "恢复自 v" + version);
        return Result.ok();
    }

    // ---------------- 评审流 ----------------

    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable String id, @RequestBody Map<String, String> body) {
        CaseEntity c = caseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("用例不存在");
        }
        String action = body.getOrDefault("action", "submit");
        String fromStatus = c.getStatus();
        String toStatus = switch (action) {
            case "submit" -> "reviewing";
            case "approve" -> "pass";
            case "reject" -> "draft";
            default -> throw BizException.badRequest("未知评审动作: " + action);
        };
        c.setStatus(toStatus);
        caseMapper.updateById(c);

        CaseReviewEntity review = new CaseReviewEntity();
        review.setCaseId(id);
        review.setReviewerId(UserContext.userId());
        review.setReviewerName(UserContext.username());
        review.setAction(action);
        review.setComment(body.get("comment"));
        review.setFromStatus(fromStatus);
        review.setToStatus(toStatus);
        review.setCreatedAt(Instant.now());
        caseReviewMapper.insert(review);
        return Result.ok();
    }

    @GetMapping("/{id}/reviews")
    public Result<List<CaseReviewEntity>> reviews(@PathVariable String id) {
        return Result.ok(caseReviewMapper.selectList(
                new LambdaQueryWrapper<CaseReviewEntity>()
                        .eq(CaseReviewEntity::getCaseId, id)
                        .orderByDesc(CaseReviewEntity::getCreatedAt)));
    }

    // ---------------- 调试执行 ----------------

    /**
     * 用例调试：同步执行并保存调试记录（大请求/响应体转存 MinIO）。
     */
    @AuditLog(action = "debug", entityType = "case")
    @PostMapping("/{id}/debug")
    public Result<DebugRecordEntity> debug(@PathVariable String id,
                                           @RequestBody(required = false) Map<String, Object> body) {
        CaseEntity c = caseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("用例不存在");
        }
        String environmentId = body == null ? null
                : (body.get("environmentId") == null ? null : String.valueOf(body.get("environmentId")));

        Map<String, String> vars = new LinkedHashMap<>();
        vars.putAll(executionSupport.buildEnvironmentVariables(environmentId));
        vars.putAll(executionSupport.buildGlobalVariables(c.getProjectId()));
        EngineDtos.ExecutionResult result = executionSupport.caseRunner()
                .run(c.getSteps(), new VariablesResolver(vars));

        DebugRecordEntity record = new DebugRecordEntity();
        record.setCaseId(id);
        record.setCaseNameSnapshot(c.getName());
        record.setEnvironmentId(environmentId);
        record.setExecuteMode("local");
        record.setResult(result.getResult());
        record.setTotalDuration((int) result.getTotalDurationMs());
        record.setRequestSummary(JsonUtils.toJson(result.getSteps().stream()
                .map(s -> Map.of("stepName", s.getStepName() == null ? "" : s.getStepName(),
                        "requestSummary", s.getRequestSummary() == null ? "" : s.getRequestSummary()))
                .toList()));
        record.setResponseSummary(JsonUtils.toJson(result.getSteps().stream()
                .map(s -> Map.of("stepName", s.getStepName() == null ? "" : s.getStepName(),
                        "responseSummary", s.getResponseSummary() == null ? "" : s.getResponseSummary()))
                .toList()));
        record.setAssertionResults(JsonUtils.toJson(result.getSteps().stream()
                .flatMap(s -> s.getAssertions().stream()).toList()));
        record.setExtractedVariables(JsonUtils.toJson(result.getVariables()));
        record.setStepResults(JsonUtils.toJson(result.getSteps()));
        record.setErrorLog(result.getErrorLog());
        record.setCreatedBy(UserContext.username());
        record.setCreatedAt(Instant.now());
        // 超 1MB 的步骤明细转存 MinIO
        String stepResultsJson = record.getStepResults();
        if (stepResultsJson != null && stepResultsJson.length() > 1_048_576) {
            record.setRequestBodyRef(ossService.putIfLarge(stepResultsJson,
                    "debug-records/" + id + "/" + System.currentTimeMillis() + "/steps.json"));
            record.setStepResults("{}");
        }
        debugRecordMapper.insert(record);
        return Result.ok(record);
    }

    @GetMapping("/{id}/debug-records")
    public Result<List<DebugRecordEntity>> debugRecords(@PathVariable String id,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        Page<DebugRecordEntity> result = debugRecordMapper.selectPage(
                new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<DebugRecordEntity>()
                        .eq(DebugRecordEntity::getCaseId, id)
                        .orderByDesc(DebugRecordEntity::getCreatedAt));
        return Result.ok(result.getRecords());
    }

    // ---------------- 回收站 ----------------

    @GetMapping("/recycle-bin")
    public Result<List<CaseEntity>> recycleBin(@RequestParam String projectId) {
        return Result.ok(caseMapper.selectList(
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .isNotNull(CaseEntity::getDeletedAt)
                        .orderByDesc(CaseEntity::getDeletedAt)));
    }

    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable String id) {
        CaseEntity c = caseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("用例不存在");
        }
        c.setDeletedAt(null);
        c.setStatus("draft");
        caseMapper.updateById(c);
        return Result.ok();
    }

    @DeleteMapping("/{id}/purge")
    public Result<Void> purge(@PathVariable String id) {
        caseMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- 内部 ----------------

    private void saveVersion(CaseEntity c, String summary) {
        CaseVersionEntity version = new CaseVersionEntity();
        version.setCaseId(c.getId());
        version.setVersion(c.getVersion());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", c.getName());
        snapshot.put("description", c.getDescription());
        snapshot.put("status", c.getStatus());
        snapshot.put("priority", c.getPriority());
        snapshot.put("tags", JsonUtils.toList(c.getTags()));
        snapshot.put("steps", JsonUtils.toList(c.getSteps()));
        version.setSnapshot(JsonUtils.toJson(snapshot));
        version.setChangeSummary(summary);
        version.setCreatedBy(UserContext.username());
        version.setCreatedAt(Instant.now());
        caseVersionMapper.insert(version);
    }
}
