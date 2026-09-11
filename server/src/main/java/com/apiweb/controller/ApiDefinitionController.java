package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.CaseStepMapper;
import com.apiweb.openapi.CaseGenerator;
import com.apiweb.openapi.SwaggerImporter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 接口定义管理（接口库）。
 *
 * <h3>API</h3>
 * <ul>
 *   <li>GET    /api/v1/apis — 分页列表</li>
 *   <li>GET    /api/v1/apis/{id} — 详情</li>
 *   <li>POST   /api/v1/apis — 新增</li>
 *   <li>PUT    /api/v1/apis/{id} — 更新</li>
 *   <li>DELETE /api/v1/apis/{id} — 软删除(到回收站)</li>
 *   <li>POST   /api/v1/apis/import-url — 从 Swagger URL 导入</li>
 *   <li>POST   /api/v1/apis/import-file — 从 Swagger 文件导入</li>
 *   <li>POST   /api/v1/apis/{id}/sync — 同步差异（重导入同源）</li>
 *   <li>POST   /api/v1/apis/{id}/generate-case — 一键生成用例</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/apis")
@RequiredArgsConstructor
public class ApiDefinitionController {

    private final ApiDefinitionMapper apiMapper;
    private final SwaggerImporter swaggerImporter;
    private final CaseGenerator caseGenerator;
    private final CaseMapper caseMapper;
    private final CaseStepMapper caseStepMapper;

    @GetMapping
    public Result<List<ApiDefinitionEntity>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<ApiDefinitionEntity> result = apiMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .isNull(ApiDefinitionEntity::getDeletedAt)
                        .eq(moduleId != null && !moduleId.isBlank(),
                                ApiDefinitionEntity::getModuleId, moduleId)
                        .like(keyword != null && !keyword.isBlank(),
                                ApiDefinitionEntity::getName,keyword)
                        .eq(method != null && !method.isBlank(),
                                ApiDefinitionEntity::getMethod, method)
                        .orderByDesc(ApiDefinitionEntity::getUpdatedAt));
        return Result.ok(result.getRecords());
    }

    @GetMapping("/{id}")
    public Result<ApiDefinitionEntity> get(@PathVariable String id) {
        ApiDefinitionEntity api = apiMapper.selectById(id);
        if (api == null) throw BizException.notFound("接口不存在");
        return Result.ok(api);
    }

    @AuditLog(action = "create", entityType = "api")
    @PostMapping
    public Result<ApiDefinitionEntity> create(@RequestBody ApiDefinitionEntity api) {
        if (api.getName() == null || api.getMethod() == null || api.getPath() == null) {
            throw BizException.badRequest("name/method/path 必填");
        }
        initDefaults(api);
        apiMapper.insert(api);
        return Result.ok(api);
    }

    @AuditLog(action = "update", entityType = "api")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody ApiDefinitionEntity api) {
        if (apiMapper.selectById(id) == null) throw BizException.notFound("接口不存在");
        api.setId(id);
        apiMapper.updateById(api);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "api")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        ApiDefinitionEntity api = apiMapper.selectById(id);
        if (api == null) throw BizException.notFound("接口不存在");
        api.setDeletedAt(Instant.now());
        apiMapper.updateById(api);
        return Result.ok();
    }

    // ============================================================
    // Swagger 导入（按 §5.5）
    // ============================================================

    /**
     * 从 URL 导入 OpenAPI/Swagger 文档。
     */
    @AuditLog(action = "import_url", entityType = "api")
    @PostMapping("/import-url")
    public Result<ImportResult> importFromUrl(@RequestBody ImportUrlRequest req) {
        try {
            List<SwaggerImporter.ApiDefinitionDraft> drafts =
                    swaggerImporter.importFromUrl(req.getUrl());
            int inserted = saveDrafts(drafts, req);
            return Result.ok(new ImportResult(drafts.size(), inserted));
        } catch (Exception e) {
            throw BizException.badRequest("Swagger 导入失败: " + e.getMessage());
        }
    }

    /**
     * 从 Swagger JSON 对象导入（前端工作台「批量导入 Swagger JSON」直接传 JSON 对象）。
     * 接收任意 JSON 对象，内部序列化为字符串后调用 importFromJson。
     */
    @AuditLog(action = "import_swagger", entityType = "api")
    @PostMapping("/import-swagger")
    public Result<ImportResult> importFromSwagger(@RequestBody Map<String, Object> swagger,
                                                  @RequestParam String projectId,
                                                  @RequestParam(required = false) String moduleId) {
        try {
            String json = com.apiweb.util.JsonUtils.toJson(swagger);
            List<SwaggerImporter.ApiDefinitionDraft> drafts = swaggerImporter.importFromJson(json);
            ImportUrlRequest req = new ImportUrlRequest();
            req.setProjectId(projectId);
            req.setModuleId(moduleId);
            int inserted = saveDrafts(drafts, req);
            return Result.ok(new ImportResult(drafts.size(), inserted));
        } catch (Exception e) {
            throw BizException.badRequest("Swagger 导入失败: " + e.getMessage());
        }
    }

    /**
     * 从上传的文件导入。
     */
    @AuditLog(action = "import_file", entityType = "api")
    @PostMapping("/import-file")
    public Result<ImportResult> importFromFile(@RequestParam("file") MultipartFile file,
                                               @RequestParam String projectId,
                                               @RequestParam(required = false) String moduleId) {
        try {
            byte[] bytes = file.getBytes();
            List<SwaggerImporter.ApiDefinitionDraft> drafts =
                    swaggerImporter.importFromFileContent(bytes);
            ImportUrlRequest req = new ImportUrlRequest();
            req.setProjectId(projectId);
            req.setModuleId(moduleId);
            int inserted = saveDrafts(drafts, req);
            return Result.ok(new ImportResult(drafts.size(), inserted));
        } catch (Exception e) {
            throw BizException.badRequest("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 同步差异：重新拉取 sourceUrl,对比 SHA-256 决定是否更新现有接口。
     */
    @AuditLog(action = "sync", entityType = "api")
    @PostMapping("/{id}/sync")
    public Result<SyncResult> sync(@PathVariable String id) {
        ApiDefinitionEntity api = apiMapper.selectById(id);
        if (api == null) throw BizException.notFound("接口不存在");
        if (api.getSourceUrl() == null) throw BizException.badRequest("该接口没有源 URL");
        try {
            List<SwaggerImporter.ApiDefinitionDraft> drafts = swaggerImporter.importFromUrl(api.getSourceUrl());
            SwaggerImporter.ApiDefinitionDraft matched = drafts.stream()
                    .filter(d -> d.getMethod().equalsIgnoreCase(api.getMethod())
                            && d.getPath().equals(api.getPath()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("源文档中找不到匹配的接口"));
            String newHash = swaggerImporter.sha256(matched.getSourceContent() == null ? "" : matched.getSourceContent());
            if (newHash.equals(api.getSourceHash())) {
                return Result.ok(new SyncResult(false, "无变化"));
            }
            // 有变化：更新字段
            api.setName(matched.getName());
            api.setDescription(matched.getDescription());
            api.setPathParams(com.apiweb.util.JsonUtils.toJson(matched.getPathParams()));
            api.setQuery(com.apiweb.util.JsonUtils.toJson(matched.getQuery()));
            api.setHeaders(com.apiweb.util.JsonUtils.toJson(matched.getHeaders()));
            api.setBodyType(matched.getBodyType() == null ? "NONE" : matched.getBodyType());
            api.setBodySchema(matched.getBodySchema() == null ? "{}" :
                    com.apiweb.util.JsonUtils.toJson(matched.getBodySchema()));
            api.setBody(matched.getBody());
            api.setResponseExamples(com.apiweb.util.JsonUtils.toJson(matched.getResponseExamples()));
            api.setResponseHeaders(com.apiweb.util.JsonUtils.toJson(matched.getResponseHeaders()));
            api.setTags(com.apiweb.util.JsonUtils.toJson(matched.getTags()));
            api.setSourceHash(newHash);
            apiMapper.updateById(api);
            return Result.ok(new SyncResult(true, "已更新"));
        } catch (Exception e) {
            throw BizException.badRequest("同步失败: " + e.getMessage());
        }
    }

    /**
     * 一键生成用例：基于 ApiDefinition 生成 CaseEntity + 1 个 HTTP_REQUEST 步骤。
     */
    @AuditLog(action = "generate_case", entityType = "api")
    @PostMapping("/{id}/generate-case")
    public Result<CaseEntity> generateCase(@PathVariable String id) {
        ApiDefinitionEntity api = apiMapper.selectById(id);
        if (api == null) throw BizException.notFound("接口不存在");
        CaseGenerator.GenerateResult r = caseGenerator.generate(api, api.getProjectId(), api.getModuleId());
        caseMapper.insert(r.getCaseEntity());
        CaseStepEntity step = r.getStep();
        step.setCaseId(r.getCaseEntity().getId());
        caseStepMapper.insert(step);
        return Result.ok(r.getCaseEntity());
    }

    // ============================================================
    // 内部
    // ============================================================

    private int saveDrafts(List<SwaggerImporter.ApiDefinitionDraft> drafts, ImportUrlRequest req) {
        int inserted = 0;
        for (SwaggerImporter.ApiDefinitionDraft draft : drafts) {
            ApiDefinitionEntity e = swaggerImporter.toEntity(draft, req.getProjectId(), req.getModuleId());
            // 跳过已存在(method+path 唯一)
            Long existing = apiMapper.selectCount(
                    new LambdaQueryWrapper<ApiDefinitionEntity>()
                            .eq(ApiDefinitionEntity::getProjectId, req.getProjectId())
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

    @Data
    public static class ImportUrlRequest {
        private String url;
        private String projectId;
        private String moduleId;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class ImportResult {
        private int total;
        private int inserted;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class SyncResult {
        private boolean changed;
        private String message;
    }
}