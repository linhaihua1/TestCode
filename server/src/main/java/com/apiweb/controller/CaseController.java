package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.VariablesResolver;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseReviewEntity;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.entity.CaseVersionEntity;
import com.apiweb.entity.DebugRecordEntity;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.CaseReviewMapper;
import com.apiweb.mapper.CaseStepMapper;
import com.apiweb.mapper.CaseVersionMapper;
import com.apiweb.mapper.DebugRecordMapper;
import com.apiweb.security.UserContext;
import com.apiweb.service.ExecutionSupportService;
import com.apiweb.service.OssKeys;
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
    private final CaseStepMapper caseStepMapper;
    private final DebugRecordMapper debugRecordMapper;
    private final ExecutionSupportService executionSupport;
    private final OssService ossService;

    // ---------------- CRUD ----------------

    /**
     * 分页查询用例列表。
     * <p>支持按模块、状态、关键字（名称模糊）、标签过滤；仅返回未删除用例；
     * 排序先按 sortOrder 升序（保持手工排序），再按更新时间倒序。
     *
     * @param projectId 所属项目 ID（必填）
     * @param moduleId  模块 ID（可选）
     * @param status    用例状态（可选，如 draft/reviewing/pass）
     * @param keyword   关键字（可选），按用例名模糊匹配
     * @param tag       标签（可选），按标签过滤
     * @param page      页码，默认 1
     * @param size      每页条数，默认 50，上限 200（防止单页拉取过多）
     * @return 当前页用例列表（不含已软删除记录）
     */
    @GetMapping
    public Result<List<CaseEntity>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        // 单页上限 200，避免大页查询拖垮 DB
        Page<CaseEntity> result = caseMapper.selectPage(new Page<>(page, Math.min(size, 200)),
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .isNull(CaseEntity::getDeletedAt) // 过滤软删除用例
                        .eq(moduleId != null && !moduleId.isBlank(), CaseEntity::getModuleId, moduleId)
                        .eq(status != null && !status.isBlank(), CaseEntity::getStatus, status)
                        .like(keyword != null && !keyword.isBlank(), CaseEntity::getName, keyword)
                        .orderByAsc(CaseEntity::getSortOrder)
                        .orderByDesc(CaseEntity::getUpdatedAt));
        return Result.ok(result.getRecords());
    }

    /**
     * 获取单个用例详情。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 用例实体（含 steps/tags 等全量字段）
     * @throws BizException 用例不存在时抛 notFound
     */
    @GetMapping("/{id}")
    public Result<CaseEntity> get(@PathVariable String id) {
        CaseEntity c = caseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("用例不存在");
        }
        return Result.ok(c);
    }

    /**
     * 新建用例。
     * <p>创建时：补齐 steps/tags 默认空数组、初始化版本号为 1；
     * 同步扁平步骤 JSON 到 t_case_step 表（任务执行器读取该表），并落一条初始版本快照。
     *
     * @param c 用例实体（请求体），name 必填
     * @return 已落库的用例实体
     * @throws BizException 用例名称为空时抛 badRequest
     * @audit 写审计日志：action=create, entityType=case
     */
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
        // 同步步骤到 t_case_step 表（任务执行器 runCaseSteps 读取此表）
        syncStepsFromJson(c.getId(), c.getSteps());
        saveVersion(c, "初始版本");
        return Result.ok(c);
    }

    /**
     * 更新用例。
     * <p>版本号自增后落库，重新同步步骤到 t_case_step 表，并记录一条版本快照。
     *
     * @param id 用例 ID（路径参数，必填）
     * @param c  用例实体（请求体）
     * @return 无业务数据
     * @throws BizException 用例不存在时抛 notFound
     * @audit 写审计日志：action=update, entityType=case
     */
    @AuditLog(action = "update", entityType = "case")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody CaseEntity c) {
        CaseEntity existing = caseMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("用例不存在");
        }
        c.setId(id);
        // 每次更新版本号自增，配合 saveVersion 形成可回溯的版本历史
        c.setVersion(existing.getVersion() + 1);
        caseMapper.updateById(c);
        // 同步步骤到 t_case_step 表
        syncStepsFromJson(id, c.getSteps());
        saveVersion(c, "更新");
        return Result.ok();
    }

    /**
     * 软删除用例到回收站：打 deletedAt 并将状态置为 trash（便于恢复，不在列表中展示）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 无业务数据（用例不存在时静默成功）
     * @audit 写审计日志：action=delete, entityType=case
     */
    @AuditLog(action = "delete", entityType = "case")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        CaseEntity c = caseMapper.selectById(id);
        if (c != null) {
            c.setDeletedAt(Instant.now());
            // 软删除同时置状态为 trash，使前端列表与统计按状态过滤时自动隐藏
            c.setStatus("trash");
            caseMapper.updateById(c);
        }
        return Result.ok();
    }

    // ---------------- 版本历史 ----------------

    /**
     * 获取用例的版本历史列表（按版本号倒序）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 版本快照列表，每条含名称/步骤/状态的版本化快照
     * @throws BizException 用例不存在时抛 notFound
     */
    @GetMapping("/{id}/versions")
    public Result<List<CaseVersionEntity>> versions(@PathVariable String id) {
        return Result.ok(caseVersionMapper.selectList(
                new LambdaQueryWrapper<CaseVersionEntity>()
                        .eq(CaseVersionEntity::getCaseId, id)
                        .orderByDesc(CaseVersionEntity::getVersion)));
    }

    /**
     * 从指定历史版本恢复用例：覆盖名称与步骤，并生成一条新版本快照。
     *
     * @param id      用例 ID（路径参数，必填）
     * @param version 目标版本号（路径参数，必填）
     * @return 无业务数据
     * @throws BizException 用例不存在或版本不存在时抛 notFound
     */
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

    /**
     * 用例评审动作：提交/通过/驳回，驱动状态流转并记录评审流水。
     * <p>状态机：submit→reviewing、approve→pass、reject→draft；非法动作抛 badRequest。
     *
     * @param id   用例 ID（路径参数，必填）
     * @param body 请求体，含 action（submit/approve/reject，默认 submit）与 comment（评审意见，可选）
     * @return 无业务数据
     * @throws BizException 用例不存在抛 notFound；动作非法抛 badRequest
     */
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

    /**
     * 获取某用例的评审流水记录（按时间倒序）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 评审记录列表（含动作、评审人、前后状态、意见）
     */
    @GetMapping("/{id}/reviews")
    public Result<List<CaseReviewEntity>> reviews(@PathVariable String id) {
        return Result.ok(caseReviewMapper.selectList(
                new LambdaQueryWrapper<CaseReviewEntity>()
                        .eq(CaseReviewEntity::getCaseId, id)
                        .orderByDesc(CaseReviewEntity::getCreatedAt)));
    }

    // ---------------- 调试执行 ----------------

    /**
     * 用例调试：在当前线程同步执行用例步骤并保存调试记录。
     * <p>步骤明细超过阈值（默认 1MB）时转存对象存储，数据库仅保留引用（requestBodyRef），避免大报文撑大表。
     *
     * @param id   用例 ID（路径参数，必填）
     * @param body 请求体（可选），可携带 environmentId 指定调试环境；为空则用默认环境
     * @return 调试记录实体（含每步请求/响应摘要、断言结果、提取变量等）
     * @throws BizException 用例不存在时抛 notFound
     * @audit 写审计日志：action=debug, entityType=case
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
        // 超过阈值（默认 1MB）的步骤明细转存对象存储，数据库只存引用
        String stepResultsJson = record.getStepResults();
        if (ossService.exceedsThreshold(stepResultsJson)) {
            record.setRequestBodyRef(ossService.putIfLarge(stepResultsJson,
                    OssKeys.debugRecordSteps(id, System.currentTimeMillis())));
            record.setStepResults("{}");
        }
        debugRecordMapper.insert(record);
        return Result.ok(record);
    }

    /**
     * 分页查询用例的调试记录列表（按创建时间倒序）。
     *
     * @param id   用例 ID（路径参数，必填）
     * @param page 页码，默认 1
     * @param size 每页条数，默认 20，上限 100
     * @return 当前页调试记录列表
     */
    @GetMapping("/{id}/debug-records")
    public Result<List<DebugRecordEntity>> debugRecords(@PathVariable String id,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        Page<DebugRecordEntity> result = debugRecordMapper.selectPage(
                // 单页上限 100，防止大页查询
                new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<DebugRecordEntity>()
                        .eq(DebugRecordEntity::getCaseId, id)
                        .orderByDesc(DebugRecordEntity::getCreatedAt));
        return Result.ok(result.getRecords());
    }

    // ---------------- 回收站 ----------------

    /**
     * 查询回收站中指定项目的已软删除用例（按删除时间倒序）。
     *
     * @param projectId 项目 ID（必填）
     * @return 已删除用例列表
     */
    @GetMapping("/recycle-bin")
    public Result<List<CaseEntity>> recycleBin(@RequestParam String projectId) {
        return Result.ok(caseMapper.selectList(
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .isNotNull(CaseEntity::getDeletedAt)
                        .orderByDesc(CaseEntity::getDeletedAt)));
    }

    /**
     * 从回收站恢复用例：清除 deletedAt 并置状态为 draft（重新进入正常列表）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 无业务数据
     * @throws BizException 用例不存在时抛 notFound
     */
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

    /**
     * 物理删除回收站中的用例（不可恢复，区别于软删除 delete）。
     *
     * @param id 用例 ID（路径参数，必填）
     * @return 无业务数据
     */
    @DeleteMapping("/{id}/purge")
    public Result<Void> purge(@PathVariable String id) {
        caseMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- 内部 ----------------

    /**
     * 把前端编辑器的扁平步骤 JSON 同步到 t_case_step 表。
     *
     * <p>前端 {@code steps} 字段为数组，每项含：
     * {@code {type, name, position, enabled, failStrategy, method, url, headers, query, body,
     * assertions, extracts, ...}}。此处转换为 {@link CaseStepEntity}（stepType + config JSON），
     * 使任务执行器 {@code runCaseSteps} 能读取到步骤。</p>
     */
    @SuppressWarnings("unchecked")
    private void syncStepsFromJson(String caseId, String stepsJson) {
        // 先清理旧步骤
        caseStepMapper.delete(new LambdaQueryWrapper<CaseStepEntity>()
                .eq(CaseStepEntity::getCaseId, caseId));
        if (stepsJson == null || stepsJson.isBlank()) {
            return;
        }
        List<Map<String, Object>> steps;
        try {
            steps = JsonUtils.fromJson(stepsJson, List.class);
        } catch (Exception e) {
            return;
        }
        if (steps == null || steps.isEmpty()) {
            return;
        }
        int order = 0;
        for (Map<String, Object> raw : steps) {
            CaseStepEntity step = new CaseStepEntity();
            step.setCaseId(caseId);
            String type = raw.get("type") == null ? null : String.valueOf(raw.get("type"));
            step.setStepType(type == null || type.isBlank() ? "HTTP_REQUEST" : type);
            step.setName(raw.get("name") == null ? "步骤 " + (order + 1) : String.valueOf(raw.get("name")));
            String position = raw.get("position") == null ? "TEST" : String.valueOf(raw.get("position"));
            step.setPosition(position);
            step.setSortOrder(order++);
            step.setEnabled(!(raw.get("enabled") instanceof Boolean b) || b);
            step.setFailStrategy(raw.get("failStrategy") == null ? "stop" : String.valueOf(raw.get("failStrategy")));
            step.setRemark(raw.get("remark") == null ? null : String.valueOf(raw.get("remark")));

            // config：把扁平字段打包成 config JSON
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("method", raw.getOrDefault("method", "GET"));
            config.put("url", raw.getOrDefault("url", ""));
            config.put("headers", raw.getOrDefault("headers", List.of()));
            config.put("query", raw.getOrDefault("query", List.of()));
            config.put("body", raw.getOrDefault("body", ""));
            config.put("assertions", raw.getOrDefault("assertions", List.of()));
            config.put("extracts", raw.getOrDefault("extracts", List.of()));
            // 兼容旧字段名（若前端用 protocol/domain/path 组合）
            if (raw.containsKey("path") && !raw.containsKey("url")) {
                config.put("url", raw.get("path"));
            }
            step.setConfig(JsonUtils.toJson(config));
            caseStepMapper.insert(step);
        }
    }

    private void saveVersion(CaseEntity c, String summary) {
        CaseVersionEntity version = new CaseVersionEntity();
        version.setCaseId(c.getId());
        version.setVersion(c.getVersion());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", c.getName());
        snapshot.put("description", c.getDescription());
        snapshot.put("status", c.getStatus());
        snapshot.put("priority", c.getPriority());
        snapshot.put("tags", JsonUtils.parseObject(c.getTags()));
        snapshot.put("steps", JsonUtils.parseObject(c.getSteps()));
        version.setSnapshot(JsonUtils.toJson(snapshot));
        version.setChangeSummary(summary);
        version.setCreatedBy(UserContext.username());
        version.setCreatedAt(Instant.now());
        caseVersionMapper.insert(version);
    }
}
