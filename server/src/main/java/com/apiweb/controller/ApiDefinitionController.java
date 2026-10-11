package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.apidefinition.ApiDefinitionImportReq;
import com.apiweb.dto.apidefinition.ApiDefinitionReq;
import com.apiweb.entity.CaseEntity;
import com.apiweb.service.ApiDefinitionService;
import com.apiweb.vo.apidefinition.ApiDefinitionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
 *   <li>POST   /api/v1/apis/import-swagger — 从 Swagger JSON 导入</li>
 *   <li>POST   /api/v1/apis/import-file — 从 Swagger 文件导入</li>
 *   <li>POST   /api/v1/apis/{id}/sync — 同步差异</li>
 *   <li>POST   /api/v1/apis/{id}/generate-case — 一键生成用例</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/apis")
@RequiredArgsConstructor
public class ApiDefinitionController {

    private final ApiDefinitionService apiService;

    /**
     * 分页查询接口库列表。
     */
    @GetMapping
    public Result<List<ApiDefinitionVO>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(apiService.list(projectId, moduleId, keyword, method, page, size));
    }

    /**
     * 获取单个接口定义详情。
     */
    @GetMapping("/{id}")
    public Result<ApiDefinitionVO> get(@PathVariable String id) {
        return Result.ok(apiService.get(id));
    }

    /**
     * 新增接口定义。
     *
     * @audit 写审计日志：action=create, entityType=api
     */
    @AuditLog(action = "create", entityType = "api")
    @PostMapping
    public Result<ApiDefinitionVO> create(@Valid @RequestBody ApiDefinitionReq req) {
        return Result.ok(apiService.create(req));
    }

    /**
     * 更新接口定义。
     *
     * @audit 写审计日志：action=update, entityType=api
     */
    @AuditLog(action = "update", entityType = "api")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody ApiDefinitionReq req) {
        apiService.update(id, req);
        return Result.ok();
    }

    /**
     * 软删除接口（到回收站）。
     *
     * @audit 写审计日志：action=delete, entityType=api
     */
    @AuditLog(action = "delete", entityType = "api")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        apiService.delete(id);
        return Result.ok();
    }

    // ============================================================
    // Swagger 导入
    // ============================================================

    /**
     * 从 URL 导入 OpenAPI/Swagger 文档。
     *
     * @audit 写审计日志：action=import_url, entityType=api
     */
    @AuditLog(action = "import_url", entityType = "api")
    @PostMapping("/import-url")
    public Result<ApiDefinitionService.ImportResult> importFromUrl(@RequestBody ApiDefinitionImportReq req) {
        return Result.ok(apiService.importFromUrl(req));
    }

    /**
     * 从 Swagger JSON 对象导入。
     *
     * @audit 写审计日志：action=import_swagger, entityType=api
     */
    @AuditLog(action = "import_swagger", entityType = "api")
    @PostMapping("/import-swagger")
    public Result<ApiDefinitionService.ImportResult> importFromSwagger(@RequestBody Map<String, Object> swagger,
                                                                       @RequestParam String projectId,
                                                                       @RequestParam(required = false) String moduleId) {
        return Result.ok(apiService.importFromSwagger(swagger, projectId, moduleId));
    }

    /**
     * 从上传的文件导入。
     *
     * @audit 写审计日志：action=import_file, entityType=api
     */
    @AuditLog(action = "import_file", entityType = "api")
    @PostMapping("/import-file")
    public Result<ApiDefinitionService.ImportResult> importFromFile(@RequestParam("file") MultipartFile file,
                                                                    @RequestParam String projectId,
                                                                    @RequestParam(required = false) String moduleId) {
        try {
            return Result.ok(apiService.importFromFile(file.getBytes(), projectId, moduleId));
        } catch (java.io.IOException e) {
            throw com.apiweb.common.BizException.badRequest("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 同步差异。
     *
     * @audit 写审计日志：action=sync, entityType=api
     */
    @AuditLog(action = "sync", entityType = "api")
    @PostMapping("/{id}/sync")
    public Result<ApiDefinitionService.SyncResult> sync(@PathVariable String id) {
        return Result.ok(apiService.sync(id));
    }

    /**
     * 一键生成用例。
     *
     * @audit 写审计日志：action=generate_case, entityType=api
     */
    @AuditLog(action = "generate_case", entityType = "api")
    @PostMapping("/{id}/generate-case")
    public Result<CaseEntity> generateCase(@PathVariable String id) {
        return Result.ok(apiService.generateCase(id));
    }
}
