package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.CaseConverter;
import com.apiweb.dto.casee.CaseReq;
import com.apiweb.dto.casee.CaseReviewReq;
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
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.casee.CaseReviewVO;
import com.apiweb.vo.casee.CaseVO;
import com.apiweb.vo.casee.CaseVersionVO;
import com.apiweb.vo.casee.DebugRecordVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用例业务服务。
 *
 * <p>承载原 {@code CaseController} 中的业务逻辑：CRUD、版本历史、评审流、
 * 调试执行、回收站。Controller 仅保留参数校验与响应包装。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseService {

    /** 用例数据访问层 */
    private final CaseMapper caseMapper;
    /** 用例版本快照数据访问层 */
    private final CaseVersionMapper caseVersionMapper;
    /** 用例评审记录数据访问层 */
    private final CaseReviewMapper caseReviewMapper;
    /** 用例步骤服务（步骤表与执行真源 t_case.steps 的双向同步收敛于此） */
    private final CaseStepService caseStepService;
    /** 调试记录数据访问层 */
    private final DebugRecordMapper debugRecordMapper;
    /** 执行支撑服务（变量组装/执行器） */
    private final ExecutionSupportService executionSupport;
    /** 对象存储服务（大报文转存） */
    private final OssService ossService;
    /** 用例相关实体 ↔ DTO/VO 转换器 */
    private final CaseConverter converter;

    private static final long MAX_SIZE = 200;

    // ---------------- CRUD ----------------

    /**
     * 分页查询用例列表（返回当前页 VO 列表，与前端既有契约一致）。
     */
    public List<CaseVO> list(String projectId, String moduleId, String status,
                             String keyword, long page, long size) {
        Page<CaseEntity> result = caseMapper.selectPage(new Page<>(page, Math.min(size, MAX_SIZE)),
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .isNull(CaseEntity::getDeletedAt)
                        .eq(moduleId != null && !moduleId.isBlank(), CaseEntity::getModuleId, moduleId)
                        .eq(status != null && !status.isBlank(), CaseEntity::getStatus, status)
                        .like(keyword != null && !keyword.isBlank(), CaseEntity::getName, keyword)
                        .orderByAsc(CaseEntity::getSortOrder)
                        .orderByDesc(CaseEntity::getUpdatedAt));
        return converter.toVOList(result.getRecords());
    }

    /**
     * 获取单个用例详情。
     */
    public CaseVO get(String id) {
        return converter.toVO(requireCase(id));
    }

    /**
     * 新建用例。
     */
    @Transactional
    public CaseVO create(CaseReq req) {
        CaseEntity c = converter.toEntity(req);
        if (c.getSteps() == null) c.setSteps("[]");
        if (c.getTags() == null) c.setTags("[]");
        c.setVersion(1);
        caseMapper.insert(c);
        caseStepService.replaceStepsFromJson(c.getId(), c.getSteps());
        saveVersion(c, "初始版本");
        return converter.toVO(c);
    }

    /**
     * 更新用例。
     *
     * <p>部分更新语义：请求未携带 steps/tags 时沿用库中原值，
     * 否则会把执行真源 {@code t_case.steps} 置空、版本快照丢步骤。</p>
     */
    @Transactional
    public void update(String id, CaseReq req) {
        CaseEntity existing = requireCase(id);
        CaseEntity c = converter.toEntity(req);
        c.setId(id);
        c.setVersion(existing.getVersion() + 1);
        if (c.getSteps() == null) {
            c.setSteps(existing.getSteps());
        }
        if (c.getTags() == null) {
            c.setTags(existing.getTags());
        }
        caseMapper.updateById(c);
        // 仅当请求显式提供 steps 时才重建步骤表（null 表示"未提供"，由 replaceStepsFromJson 内部跳过）
        caseStepService.replaceStepsFromJson(id, req.getSteps());
        saveVersion(c, "更新");
    }

    /**
     * 软删除用例到回收站。
     */
    @Transactional
    public void delete(String id) {
        CaseEntity c = caseMapper.selectById(id);
        if (c != null) {
            c.setDeletedAt(Instant.now());
            c.setStatus("trash");
            caseMapper.updateById(c);
        }
    }

    // ---------------- 版本历史 ----------------

    /**
     * 获取用例版本历史列表。
     */
    public List<CaseVersionVO> versions(String id) {
        return converter.toVersionVOList(caseVersionMapper.selectList(
                new LambdaQueryWrapper<CaseVersionEntity>()
                        .eq(CaseVersionEntity::getCaseId, id)
                        .orderByDesc(CaseVersionEntity::getVersion)));
    }

    /**
     * 从指定历史版本恢复用例。
     */
    @Transactional
    public void restoreVersion(String id, Integer version) {
        CaseEntity c = requireCase(id);
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
        // 恢复版本时必须同步步骤表，否则 t_case.steps 与 t_case_step 漂移：
        // 直接调试走新步骤，而被引用/步骤管理接口仍是旧步骤
        caseStepService.replaceStepsFromJson(id, c.getSteps());
        saveVersion(c, "恢复自 v" + version);
    }

    // ---------------- 评审流 ----------------

    /**
     * 用例评审动作。
     */
    @Transactional
    public void review(String id, CaseReviewReq req) {
        CaseEntity c = requireCase(id);
        String action = req.getAction() == null ? "submit" : req.getAction();
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
        review.setComment(req.getComment());
        review.setFromStatus(fromStatus);
        review.setToStatus(toStatus);
        review.setCreatedAt(Instant.now());
        caseReviewMapper.insert(review);
    }

    /**
     * 获取某用例的评审流水记录。
     */
    public List<CaseReviewVO> reviews(String id) {
        return converter.toReviewVOList(caseReviewMapper.selectList(
                new LambdaQueryWrapper<CaseReviewEntity>()
                        .eq(CaseReviewEntity::getCaseId, id)
                        .orderByDesc(CaseReviewEntity::getCreatedAt)));
    }

    // ---------------- 调试执行 ----------------

    /**
     * 用例调试。
     */
    @Transactional
    public DebugRecordVO debug(String id, String environmentId) {
        CaseEntity c = requireCase(id);
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
        String stepResultsJson = record.getStepResults();
        if (ossService.exceedsThreshold(stepResultsJson)) {
            record.setRequestBodyRef(ossService.putIfLarge(stepResultsJson,
                    OssKeys.debugRecordSteps(id, System.currentTimeMillis())));
            record.setStepResults("{}");
        }
        debugRecordMapper.insert(record);
        return converter.toDebugRecordVO(record);
    }

    /**
     * 分页查询调试记录（返回当前页 VO 列表，与前端既有契约一致）。
     */
    public List<DebugRecordVO> debugRecords(String id, long page, long size) {
        Page<DebugRecordEntity> result = debugRecordMapper.selectPage(
                new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<DebugRecordEntity>()
                        .eq(DebugRecordEntity::getCaseId, id)
                        .orderByDesc(DebugRecordEntity::getCreatedAt));
        return converter.toDebugRecordVOList(result.getRecords());
    }

    /**
     * 全局调试记录：按项目聚合全部用例的调试记录（文档 §2.2 全局视图）。
     *
     * <p>DebugRecordEntity 不直接持有 projectId，先查该项目下所有未删除用例的 caseId，
     * 再 {@code in} 查询调试记录；无用例时返回空列表。</p>
     *
     * @param projectId 项目 ID
     * @param page      页码（从 1 起）
     * @param size      每页条数
     * @return 调试记录 VO 列表（按创建时间倒序）
     */
    public List<DebugRecordVO> debugRecordsByProject(String projectId, long page, long size) {
        List<String> caseIds = caseMapper.selectList(
                        new LambdaQueryWrapper<CaseEntity>()
                                .select(CaseEntity::getId)
                                .eq(CaseEntity::getProjectId, projectId))
                .stream()
                .map(CaseEntity::getId)
                .toList();
        if (caseIds.isEmpty()) {
            return List.of();
        }
        Page<DebugRecordEntity> result = debugRecordMapper.selectPage(
                new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<DebugRecordEntity>()
                        .in(DebugRecordEntity::getCaseId, caseIds)
                        .orderByDesc(DebugRecordEntity::getCreatedAt));
        return converter.toDebugRecordVOList(result.getRecords());
    }

    // ---------------- 回收站 ----------------

    /**
     * 查询回收站中指定项目的已软删除用例。
     */
    public List<CaseVO> recycleBin(String projectId) {
        return converter.toVOList(caseMapper.selectList(
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .isNotNull(CaseEntity::getDeletedAt)
                        .orderByDesc(CaseEntity::getDeletedAt)));
    }

    /**
     * 从回收站恢复用例。
     */
    @Transactional
    public void restore(String id) {
        CaseEntity c = requireCase(id);
        c.setDeletedAt(null);
        c.setStatus("draft");
        caseMapper.updateById(c);
    }

    /**
     * 物理删除回收站中的用例。
     */
    @Transactional
    public void purge(String id) {
        caseMapper.deleteById(id);
    }

    // ---------------- 内部 ----------------

    private CaseEntity requireCase(String id) {
        CaseEntity c = caseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("用例不存在");
        }
        return c;
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
