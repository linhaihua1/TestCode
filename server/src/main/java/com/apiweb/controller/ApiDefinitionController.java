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

    /**
     * 分页查询接口库列表。
     * <p>支持按模块、名称关键字（模糊）、HTTP 方法过滤；仅返回未软删除的接口，按更新时间倒序。
     *
     * @param projectId 所属项目 ID（必填），用于隔离不同项目的数据
     * @param moduleId  模块 ID（可选），为空则查该项目全部模块
     * @param keyword   关键字（可选），按接口名称模糊匹配
     * @param method    HTTP 方法（可选，如 GET/POST），按方法精确过滤
     * @param page      页码，默认 1
     * @param size      每页条数，默认 20，上限 100（防止单页拉取过多拖垮查询）
     * @return 当前页的接口定义列表（不含已删除记录）
     */
    @GetMapping
    public Result<List<ApiDefinitionEntity>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        // 单页上限 100：防止调用方传超大 size 拖垮 DB 查询
        Page<ApiDefinitionEntity> result = apiMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .isNull(ApiDefinitionEntity::getDeletedAt) // 仅查未软删除的接口
                        .eq(moduleId != null && !moduleId.isBlank(),
                                ApiDefinitionEntity::getModuleId, moduleId)
                        .like(keyword != null && !keyword.isBlank(),
                                ApiDefinitionEntity::getName,keyword)
                        .eq(method != null && !method.isBlank(),
                                ApiDefinitionEntity::getMethod, method)
                        .orderByDesc(ApiDefinitionEntity::getUpdatedAt));
        return Result.ok(result.getRecords());
    }

    /**
     * 获取单个接口定义详情。
     *
     * @param id 接口 ID（路径参数，必填）
     * @return 接口定义实体（含请求/响应结构、mock 配置等全量字段）
     * @throws BizException 接口不存在时抛出 notFound
     */
    @GetMapping("/{id}")
    public Result<ApiDefinitionEntity> get(@PathVariable String id) {
        ApiDefinitionEntity api = apiMapper.selectById(id);
        if (api == null) throw BizException.notFound("接口不存在");
        return Result.ok(api);
    }

    /**
     * 新增接口定义。
     * <p>创建时调用 initDefaults 补齐 headers/query/body 等 JSON 字段的默认值，避免下游解析空指针。
     *
     * @param api 接口定义实体（请求体），name/method/path 为必填字段
     * @return 已落库的接口定义（含生成的 ID 与默认值）
     * @throws BizException name/method/path 任意缺失时抛出 badRequest
     * @audit 写审计日志：action=create, entityType=api
     */
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

    /**
     * 更新接口定义（覆盖请求体中的非空字段，不改动 projectId 等归属信息）。
     *
     * @param id  接口 ID（路径参数，必填），用于定位待更新记录
     * @param api 接口定义实体（请求体），仅覆盖提供的字段
     * @return 无业务数据
     * @throws BizException 接口不存在时抛出 notFound
     * @audit 写审计日志：action=update, entityType=api
     */
    @AuditLog(action = "update", entityType = "api")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody ApiDefinitionEntity api) {
        if (apiMapper.selectById(id) == null) throw BizException.notFound("接口不存在");
        api.setId(id);
        apiMapper.updateById(api);
        return Result.ok();
    }

    /**
     * 软删除接口：仅打 deletedAt 时间戳，不物理删除，便于回收站恢复。
     *
     * @param id 接口 ID（路径参数，必填）
     * @return 无业务数据
     * @throws BizException 接口不存在时抛出 notFound
     * @audit 写审计日志：action=delete, entityType=api
     */
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
     * <p>拉取远端文档并按 method+path 去重，已存在的接口跳过不重复插入。
     *
     * @param req 请求体，含 url、projectId（必填）、moduleId（可选）
     * @return 导入结果：total=解析出的接口数，inserted=实际新增数
     * @throws BizException URL 拉取或解析失败时抛出 badRequest（含原始异常信息）
     * @audit 写审计日志：action=import_url, entityType=api
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
     *
     * @param swagger   Swagger/OpenAPI JSON 对象（请求体，必填）
     * @param projectId 目标项目 ID（query 参数，必填）
     * @param moduleId  目标模块 ID（query 参数，可选）
     * @return 导入结果：total=解析出的接口数，inserted=实际新增数
     * @throws BizException JSON 解析或导入失败时抛出 badRequest（含原始异常信息）
     * @audit 写审计日志：action=import_swagger, entityType=api
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
     * <p>支持 .json/.yaml 等格式；按 method+path 去重，已存在的接口跳过。
     *
     * @param file     上传的 Swagger 文件（表单字段 file，必填）
     * @param projectId 目标项目 ID（必填）
     * @param moduleId  目标模块 ID（可选）
     * @return 导入结果：total=解析出的接口数，inserted=实际新增数
     * @throws BizException 文件读取或解析失败时抛出 badRequest（含原始异常信息）
     * @audit 写审计日志：action=import_file, entityType=api
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
     * 同步差异：重新拉取 sourceUrl，对比 SHA-256 决定是否更新现有接口。
     * <p>无变化直接返回 changed=false，有变化则覆盖请求/响应字段并刷新 sourceHash。
     *
     * @param id 接口 ID（路径参数，必填）
     * @return 同步结果：changed 是否有变化，message 说明文案
     * @throws BizException 接口不存在或没有源 URL 时抛 notFound/badRequest；同步失败抛 badRequest
     * @audit 写审计日志：action=sync, entityType=api
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
     * <p>同时落库用例与步骤，步骤归属到该用例。
     *
     * @param id 接口 ID（路径参数，必填）
     * @return 生成的用例实体（含 ID）
     * @throws BizException 接口不存在时抛出 notFound
     * @audit 写审计日志：action=generate_case, entityType=api
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