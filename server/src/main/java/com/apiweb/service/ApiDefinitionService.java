package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.ApiDefinitionConverter;
import com.apiweb.dto.apidefinition.ApiDefinitionImportReq;
import com.apiweb.dto.apidefinition.ApiDefinitionReq;
import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.CaseStepMapper;
import com.apiweb.openapi.CaseGenerator;
import com.apiweb.openapi.SwaggerImporter;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.apidefinition.ApiDefinitionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 接口定义业务服务。
 *
 * <p>承载原 {@code ApiDefinitionController} 中的业务逻辑：CRUD、Swagger 导入、
 * 同步差异、一键生成用例。Controller 仅保留参数校验与响应包装。</p>
 */
@Service
@RequiredArgsConstructor
public class ApiDefinitionService {

    /** 接口定义数据访问层 */
    private final ApiDefinitionMapper apiMapper;
    /** 实体 ↔ DTO/VO 转换器 */
    private final ApiDefinitionConverter converter;
    /** Swagger/OpenAPI 导入器 */
    private final SwaggerImporter swaggerImporter;
    /** 一键生成用例服务 */
    private final CaseGenerator caseGenerator;
    /** 用例数据访问层（生成用例时落库） */
    private final CaseMapper caseMapper;
    /** 用例步骤数据访问层（生成用例步骤时落库） */
    private final CaseStepMapper caseStepMapper;

    /** 单页上限 */
    private static final long MAX_SIZE = 100;

    /**
     * 分页查询接口库列表。
     *
     * <p>返回当前页 VO 列表（与前端既有契约一致，保持数组返回）。</p>
     */
    public List<ApiDefinitionVO> list(String projectId, String moduleId, String keyword,
                                      String method, long page, long size) {
        Page<ApiDefinitionEntity> result = apiMapper.selectPage(
                new Page<>(page, Math.min(size, MAX_SIZE)),
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .isNull(ApiDefinitionEntity::getDeletedAt)
                        .eq(moduleId != null && !moduleId.isBlank(),
                                ApiDefinitionEntity::getModuleId, moduleId)
                        .like(keyword != null && !keyword.isBlank(),
                                ApiDefinitionEntity::getName, keyword)
                        .eq(method != null && !method.isBlank(),
                                ApiDefinitionEntity::getMethod, method)
                        .orderByDesc(ApiDefinitionEntity::getUpdatedAt));
        return converter.toVOList(result.getRecords());
    }

    /**
     * 获取单个接口定义详情。
     */
    public ApiDefinitionVO get(String id) {
        ApiDefinitionEntity api = requireApi(id);
        return converter.toVO(api);
    }

    /**
     * 新增接口定义。
     */
    @Transactional
    public ApiDefinitionVO create(ApiDefinitionReq req) {
        ApiDefinitionEntity api = converter.toEntity(req);
        initDefaults(api);
        apiMapper.insert(api);
        return converter.toVO(api);
    }

    /**
     * 更新接口定义。
     */
    @Transactional
    public void update(String id, ApiDefinitionReq req) {
        if (apiMapper.selectById(id) == null) throw BizException.notFound("接口不存在");
        ApiDefinitionEntity api = converter.toEntity(req);
        api.setId(id);
        apiMapper.updateById(api);
    }

    /**
     * 软删除接口（到回收站）。
     */
    @Transactional
    public void delete(String id) {
        ApiDefinitionEntity api = requireApi(id);
        api.setDeletedAt(Instant.now());
        apiMapper.updateById(api);
    }

    // ============================================================
    // Swagger 导入
    // ============================================================

    /**
     * 从 URL 导入 Swagger 文档。
     */
    @Transactional
    public ImportResult importFromUrl(ApiDefinitionImportReq req) {
        try {
            List<SwaggerImporter.ApiDefinitionDraft> drafts =
                    swaggerImporter.importFromUrl(req.getUrl());
            int inserted = saveDrafts(drafts, req.getProjectId(), req.getModuleId());
            return new ImportResult(drafts.size(), inserted);
        } catch (Exception e) {
            throw BizException.badRequest("Swagger 导入失败: " + e.getMessage());
        }
    }

    /**
     * 从 Swagger JSON 对象导入。
     */
    @Transactional
    public ImportResult importFromSwagger(Map<String, Object> swagger, String projectId, String moduleId) {
        try {
            String json = JsonUtils.toJson(swagger);
            List<SwaggerImporter.ApiDefinitionDraft> drafts = swaggerImporter.importFromJson(json);
            int inserted = saveDrafts(drafts, projectId, moduleId);
            return new ImportResult(drafts.size(), inserted);
        } catch (Exception e) {
            throw BizException.badRequest("Swagger 导入失败: " + e.getMessage());
        }
    }

    /**
     * 从上传的文件字节导入（Service 层不感知 Spring MVC 的 MultipartFile，
     * 由 Controller 负责将上传文件转换为字节数组后传入）。
     *
     * @param fileBytes 上传文件的原始字节
     * @param projectId 目标项目 ID
     * @param moduleId  目标模块 ID
     * @return 导入结果（解析数量与插入数量）
     */
    @Transactional
    public ImportResult importFromFile(byte[] fileBytes, String projectId, String moduleId) {
        try {
            List<SwaggerImporter.ApiDefinitionDraft> drafts =
                    swaggerImporter.importFromFileContent(fileBytes);
            int inserted = saveDrafts(drafts, projectId, moduleId);
            return new ImportResult(drafts.size(), inserted);
        } catch (Exception e) {
            throw BizException.badRequest("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 同步差异：重新拉取 sourceUrl 对比 SHA-256 决定是否更新。
     */
    @Transactional
    public SyncResult sync(String id) {
        ApiDefinitionEntity api = requireApi(id);
        if (api.getSourceUrl() == null) throw BizException.badRequest("该接口没有源 URL");
        try {
            List<SwaggerImporter.ApiDefinitionDraft> drafts = swaggerImporter.importFromUrl(api.getSourceUrl());
            SwaggerImporter.ApiDefinitionDraft matched = drafts.stream()
                    .filter(d -> d.getMethod().equalsIgnoreCase(api.getMethod())
                            && d.getPath().equals(api.getPath()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("源文档中找不到匹配的接口"));
            String newHash = swaggerImporter.sha256(
                    matched.getSourceContent() == null ? "" : matched.getSourceContent());
            if (newHash.equals(api.getSourceHash())) {
                return new SyncResult(false, "无变化");
            }
            api.setName(matched.getName());
            api.setDescription(matched.getDescription());
            api.setPathParams(JsonUtils.toJson(matched.getPathParams()));
            api.setQuery(JsonUtils.toJson(matched.getQuery()));
            api.setHeaders(JsonUtils.toJson(matched.getHeaders()));
            api.setBodyType(matched.getBodyType() == null ? "NONE" : matched.getBodyType());
            api.setBodySchema(matched.getBodySchema() == null ? "{}" : JsonUtils.toJson(matched.getBodySchema()));
            api.setBody(matched.getBody());
            api.setResponseExamples(JsonUtils.toJson(matched.getResponseExamples()));
            api.setResponseHeaders(JsonUtils.toJson(matched.getResponseHeaders()));
            api.setTags(JsonUtils.toJson(matched.getTags()));
            api.setSourceHash(newHash);
            apiMapper.updateById(api);
            return new SyncResult(true, "已更新");
        } catch (Exception e) {
            throw BizException.badRequest("同步失败: " + e.getMessage());
        }
    }

    /**
     * 一键生成用例。
     */
    @Transactional
    public CaseEntity generateCase(String id) {
        ApiDefinitionEntity api = requireApi(id);
        CaseGenerator.GenerateResult r = caseGenerator.generate(api, api.getProjectId(), api.getModuleId());
        caseMapper.insert(r.getCaseEntity());
        CaseStepEntity step = r.getStep();
        step.setCaseId(r.getCaseEntity().getId());
        caseStepMapper.insert(step);
        return r.getCaseEntity();
    }

    // ============================================================
    // 内部
    // ============================================================

    private ApiDefinitionEntity requireApi(String id) {
        ApiDefinitionEntity api = apiMapper.selectById(id);
        if (api == null) throw BizException.notFound("接口不存在");
        return api;
    }

    private int saveDrafts(List<SwaggerImporter.ApiDefinitionDraft> drafts, String projectId, String moduleId) {
        int inserted = 0;
        for (SwaggerImporter.ApiDefinitionDraft draft : drafts) {
            ApiDefinitionEntity e = swaggerImporter.toEntity(draft, projectId, moduleId);
            Long existing = apiMapper.selectCount(
                    new LambdaQueryWrapper<ApiDefinitionEntity>()
                            .eq(ApiDefinitionEntity::getProjectId, projectId)
                            .eq(ApiDefinitionEntity::getMethod, e.getMethod())
                            .eq(ApiDefinitionEntity::getPath, e.getPath())
                            .isNull(ApiDefinitionEntity::getDeletedAt));
            if (existing != null && existing > 0) continue;
            initDefaults(e);
            apiMapper.insert(e);
            inserted++;
        }
        return inserted;
    }

    private void initDefaults(ApiDefinitionEntity api) {
        if (api.getHeaders() == null) api.setHeaders("[]");
        if (api.getQuery() == null) api.setQuery("[]");
        if (api.getTags() == null) api.setTags("[]");
        if (api.getPathParams() == null) api.setPathParams("[]");
        if (api.getResponseExamples() == null) api.setResponseExamples("{}");
        if (api.getResponseHeaders() == null) api.setResponseHeaders("{}");
        if (api.getBodySchema() == null) api.setBodySchema("{}");
        if (api.getMockRules() == null) api.setMockRules("[]");
        if (api.getBodyType() == null) api.setBodyType("NONE");
        if (api.getMockType() == null) api.setMockType("BASIC");
        if (api.getMockStatusCode() == null) api.setMockStatusCode(200);
        if (api.getMockEnabled() == null) api.setMockEnabled(false);
    }

    /** 导入结果（total/inserted）。 */
    public record ImportResult(int total, int inserted) {
    }

    /** 同步结果（changed/message）。 */
    public record SyncResult(boolean changed, String message) {
    }
}
