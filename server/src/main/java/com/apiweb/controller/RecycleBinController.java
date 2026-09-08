package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.ModuleEntity;
import com.apiweb.entity.RecycleBinConfigEntity;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.ModuleMapper;
import com.apiweb.mapper.RecycleBinConfigMapper;
import com.apiweb.mapper.TestTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一回收站（需求文档 §2.3）。
 *
 * <p>覆盖四种回收对象：
 * <ul>
 *   <li>{@code MODULE}：目录</li>
 *   <li>{@code API}：接口定义</li>
 *   <li>{@code CASE}：接口用例</li>
 *   <li>{@code TASK}：测试任务</li>
 * </ul>
 *
 * <p>支持：列表查询、单/批量还原、单/批量永久删除、配置自动清理周期。
 *
 * <h3>边界（开发文档 §7 回收站相关）</h3>
 * <ul>
 *   <li>还原时原目录已不存在 → 还原到根目录</li>
 *   <li>还原时同名冲突 → 提示重命名后还原</li>
 *   <li>自动清理周期为"不自动清理" → 跳过清理任务</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final CaseMapper caseMapper;
    private final ModuleMapper moduleMapper;
    private final ApiDefinitionMapper apiMapper;
    private final TestTaskMapper testTaskMapper;
    private final RecycleBinConfigMapper configMapper;

    /** 回收对象类型 */
    public static final String TYPE_MODULE = "MODULE";
    public static final String TYPE_API = "API";
    public static final String TYPE_CASE = "CASE";
    public static final String TYPE_TASK = "TASK";

    /** 回收站条目统一视图（不同对象附加不同字段） */
    @Data
    public static class RecycleBinItem {
        private String id;
        private String type;
        private String name;
        private String parentPath;
        /** MODULE: 子目录+用例数 / API: method+path / CASE: 所属目录 / TASK: 任务类型 */
        private String extra;
        private String deletedBy;
        private Instant deletedAt;
    }

    // ---------------- 列表 ----------------

    /**
     * 查询回收站列表（按类型）。
     *
     * @param projectId 项目 ID
     * @param type      {@code MODULE|API|CASE|TASK}
     */
    @GetMapping
    public Result<List<RecycleBinItem>> list(@RequestParam String projectId,
                                             @RequestParam(defaultValue = "CASE") String type) {
        return switch (type) {
            case TYPE_MODULE -> Result.ok(buildModuleItems(projectId));
            case TYPE_API -> Result.ok(buildApiItems(projectId));
            case TYPE_TASK -> Result.ok(buildTaskItems(projectId));
            default -> Result.ok(buildCaseItems(projectId));
        };
    }

    private List<RecycleBinItem> buildCaseItems(String projectId) {
        List<CaseEntity> list = caseMapper.selectList(
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .isNotNull(CaseEntity::getDeletedAt)
                        .orderByDesc(CaseEntity::getDeletedAt));
        List<RecycleBinItem> out = new ArrayList<>();
        for (CaseEntity c : list) {
            RecycleBinItem it = new RecycleBinItem();
            it.setId(c.getId());
            it.setType(TYPE_CASE);
            it.setName(c.getName());
            it.setExtra("所属目录: " + (c.getModuleId() == null ? "根" : c.getModuleId()));
            it.setDeletedAt(c.getDeletedAt());
            out.add(it);
        }
        return out;
    }

    private List<RecycleBinItem> buildModuleItems(String projectId) {
        List<ModuleEntity> list = moduleMapper.selectList(
                new LambdaQueryWrapper<ModuleEntity>()
                        .eq(ModuleEntity::getProjectId, projectId)
                        .isNotNull(ModuleEntity::getDeletedAt)
                        .orderByDesc(ModuleEntity::getDeletedAt));
        List<RecycleBinItem> out = new ArrayList<>();
        for (ModuleEntity m : list) {
            RecycleBinItem it = new RecycleBinItem();
            it.setId(m.getId());
            it.setType(TYPE_MODULE);
            it.setName(m.getName());
            it.setExtra("目录类型: " + m.getType());
            it.setDeletedAt(m.getDeletedAt());
            out.add(it);
        }
        return out;
    }

    private List<RecycleBinItem> buildApiItems(String projectId) {
        List<ApiDefinitionEntity> list = apiMapper.selectList(
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .isNotNull(ApiDefinitionEntity::getDeletedAt)
                        .orderByDesc(ApiDefinitionEntity::getDeletedAt));
        List<RecycleBinItem> out = new ArrayList<>();
        for (ApiDefinitionEntity a : list) {
            RecycleBinItem it = new RecycleBinItem();
            it.setId(a.getId());
            it.setType(TYPE_API);
            it.setName(a.getName());
            it.setExtra(a.getMethod() + " " + a.getPath());
            it.setDeletedAt(a.getDeletedAt());
            out.add(it);
        }
        return out;
    }

    private List<RecycleBinItem> buildTaskItems(String projectId) {
        List<TestTaskEntity> list = testTaskMapper.selectList(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getProjectId, projectId)
                        .isNotNull(TestTaskEntity::getDeletedAt)
                        .orderByDesc(TestTaskEntity::getDeletedAt));
        List<RecycleBinItem> out = new ArrayList<>();
        for (TestTaskEntity t : list) {
            RecycleBinItem it = new RecycleBinItem();
            it.setId(t.getId());
            it.setType(TYPE_TASK);
            it.setName(t.getName());
            boolean scheduled = t.getCronExpr() != null && !t.getCronExpr().isBlank();
            it.setExtra(scheduled ? "定时任务" : "普通任务");
            it.setDeletedAt(t.getDeletedAt());
            out.add(it);
        }
        return out;
    }

    // ---------------- 还原 ----------------

    /**
     * 批量还原：请求体 { ids: [...], type: "MODULE|API|CASE|TASK" }。
     *
     * <p>边界：还原模块时若原 parentId 已不存在则还原到根目录。
     */
    @AuditLog(action = "restore", entityType = "recycle_bin")
    @PostMapping("/restore")
    public Result<Map<String, Object>> restore(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        String type = String.valueOf(body.getOrDefault("type", TYPE_CASE));
        if (ids.isEmpty()) {
            throw BizException.badRequest("ids 不能为空");
        }
        int restored = 0;
        List<String> renamed = new ArrayList<>();
        switch (type) {
            case TYPE_MODULE -> {
                for (String id : ids) {
                    ModuleEntity m = moduleMapper.selectById(id);
                    if (m != null) {
                        // 若原 parentId 已不存在 → 还原到根
                        if (m.getParentId() != null && moduleMapper.selectById(m.getParentId()) == null) {
                            m.setParentId(null);
                        }
                        // 同名冲突提示重命名
                        if (isModuleNameConflict(m)) {
                            m.setName(m.getName() + "（还原）");
                            renamed.add(m.getId());
                        }
                        m.setDeletedAt(null);
                        moduleMapper.updateById(m);
                        restored++;
                    }
                }
            }
            case TYPE_API -> {
                for (String id : ids) {
                    ApiDefinitionEntity a = apiMapper.selectById(id);
                    if (a != null) {
                        if (isApiNameConflict(a)) {
                            a.setName(a.getName() + "（还原）");
                            renamed.add(a.getId());
                        }
                        a.setDeletedAt(null);
                        apiMapper.updateById(a);
                        restored++;
                    }
                }
            }
            case TYPE_TASK -> {
                for (String id : ids) {
                    TestTaskEntity t = testTaskMapper.selectById(id);
                    if (t != null) {
                        if (isTaskNameConflict(t)) {
                            t.setName(t.getName() + "（还原）");
                            renamed.add(t.getId());
                        }
                        t.setDeletedAt(null);
                        testTaskMapper.updateById(t);
                        restored++;
                    }
                }
            }
            default -> {
                for (String id : ids) {
                    CaseEntity c = caseMapper.selectById(id);
                    if (c != null) {
                        if (isCaseNameConflict(c)) {
                            c.setName(c.getName() + "（还原）");
                            renamed.add(c.getId());
                        }
                        c.setDeletedAt(null);
                        c.setStatus("draft");
                        caseMapper.updateById(c);
                        restored++;
                    }
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restoredCount", restored);
        if (!renamed.isEmpty()) {
            result.put("renamedIds", renamed);
            result.put("message", "部分对象存在同名冲突，已自动添加“（还原）”后缀");
        }
        return Result.ok(result);
    }

    // ---------------- 永久删除 ----------------

    /**
     * 批量永久删除：请求体 { ids: [...], type: "..." }。
     *
     * <p>注意：永久删除不可恢复，需前端二次确认。
     */
    @AuditLog(action = "permanent_delete", entityType = "recycle_bin")
    @DeleteMapping("/permanent")
    public Result<Void> permanent(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        String type = String.valueOf(body.getOrDefault("type", TYPE_CASE));
        if (ids.isEmpty()) {
            throw BizException.badRequest("ids 不能为空");
        }
        switch (type) {
            case TYPE_MODULE -> ids.forEach(moduleMapper::deleteById);
            case TYPE_API -> ids.forEach(apiMapper::deleteById);
            case TYPE_TASK -> ids.forEach(testTaskMapper::deleteById);
            default -> ids.forEach(caseMapper::deleteById);
        }
        return Result.ok();
    }

    /**
     * 清空回收站（仅清空指定类型且超过 N 天的项）。
     */
    @AuditLog(action = "purge_all", entityType = "recycle_bin")
    @DeleteMapping("/purge")
    public Result<Integer> purgeOld(@RequestParam String projectId,
                                    @RequestParam(defaultValue = "CASE") String type,
                                    @RequestParam(defaultValue = "0") int olderThanDays) {
        Instant threshold = olderThanDays > 0
                ? Instant.now().minusSeconds(olderThanDays * 86400L)
                : Instant.now().minusSeconds(86400L);
        int count = switch (type) {
            case TYPE_MODULE -> {
                List<ModuleEntity> list = moduleMapper.selectList(
                        new LambdaQueryWrapper<ModuleEntity>()
                                .eq(ModuleEntity::getProjectId, projectId)
                                .lt(ModuleEntity::getDeletedAt, threshold));
                list.forEach(m -> moduleMapper.deleteById(m.getId()));
                yield list.size();
            }
            case TYPE_API -> {
                List<ApiDefinitionEntity> list = apiMapper.selectList(
                        new LambdaQueryWrapper<ApiDefinitionEntity>()
                                .eq(ApiDefinitionEntity::getProjectId, projectId)
                                .lt(ApiDefinitionEntity::getDeletedAt, threshold));
                list.forEach(a -> apiMapper.deleteById(a.getId()));
                yield list.size();
            }
            case TYPE_TASK -> {
                List<TestTaskEntity> list = testTaskMapper.selectList(
                        new LambdaQueryWrapper<TestTaskEntity>()
                                .eq(TestTaskEntity::getProjectId, projectId)
                                .lt(TestTaskEntity::getDeletedAt, threshold));
                list.forEach(t -> testTaskMapper.deleteById(t.getId()));
                yield list.size();
            }
            default -> {
                List<CaseEntity> list = caseMapper.selectList(
                        new LambdaQueryWrapper<CaseEntity>()
                                .eq(CaseEntity::getProjectId, projectId)
                                .lt(CaseEntity::getDeletedAt, threshold));
                list.forEach(c -> caseMapper.deleteById(c.getId()));
                yield list.size();
            }
        };
        return Result.ok(count);
    }

    // ---------------- 自动清理配置 ----------------

    /**
     * 获取项目的回收站自动清理配置。
     */
    @GetMapping("/config")
    public Result<RecycleBinConfigEntity> getConfig(@RequestParam String projectId) {
        RecycleBinConfigEntity cfg = configMapper.selectOne(
                new LambdaQueryWrapper<RecycleBinConfigEntity>()
                        .eq(RecycleBinConfigEntity::getProjectId, projectId));
        if (cfg == null) {
            cfg = new RecycleBinConfigEntity();
            cfg.setProjectId(projectId);
            cfg.setCleanupDays(30);
        }
        return Result.ok(cfg);
    }

    /**
     * 配置自动清理周期（开发文档 §3 + §7）。{@code cleanupDays=0} 表示不自动清理。
     */
    @AuditLog(action = "update", entityType = "recycle_bin_config")
    @PutMapping("/config")
    public Result<Void> updateConfig(@RequestBody RecycleBinConfigEntity cfg) {
        if (cfg.getProjectId() == null || cfg.getProjectId().isBlank()) {
            throw BizException.badRequest("projectId 必填");
        }
        if (cfg.getCleanupDays() == null || cfg.getCleanupDays() < 0) {
            throw BizException.badRequest("cleanupDays 必须 >= 0");
        }
        RecycleBinConfigEntity existing = configMapper.selectOne(
                new LambdaQueryWrapper<RecycleBinConfigEntity>()
                        .eq(RecycleBinConfigEntity::getProjectId, cfg.getProjectId()));
        if (existing == null) {
            cfg.setId(null);
            configMapper.insert(cfg);
        } else {
            existing.setCleanupDays(cfg.getCleanupDays());
            configMapper.updateById(existing);
        }
        return Result.ok();
    }

    // ---------------- 同名冲突检测 ----------------

    private boolean isCaseNameConflict(CaseEntity c) {
        Long same = caseMapper.selectCount(
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, c.getProjectId())
                        .eq(CaseEntity::getModuleId, c.getModuleId())
                        .eq(CaseEntity::getName, c.getName())
                        .isNull(CaseEntity::getDeletedAt));
        return same != null && same > 0;
    }

    private boolean isModuleNameConflict(ModuleEntity m) {
        Long same = moduleMapper.selectCount(
                new LambdaQueryWrapper<ModuleEntity>()
                        .eq(ModuleEntity::getProjectId, m.getProjectId())
                        .eq(ModuleEntity::getParentId, m.getParentId())
                        .eq(ModuleEntity::getName, m.getName())
                        .isNull(ModuleEntity::getDeletedAt));
        return same != null && same > 0;
    }

    private boolean isApiNameConflict(ApiDefinitionEntity a) {
        Long same = apiMapper.selectCount(
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, a.getProjectId())
                        .eq(ApiDefinitionEntity::getName, a.getName())
                        .isNull(ApiDefinitionEntity::getDeletedAt));
        return same != null && same > 0;
    }

    private boolean isTaskNameConflict(TestTaskEntity t) {
        Long same = testTaskMapper.selectCount(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getProjectId, t.getProjectId())
                        .eq(TestTaskEntity::getName, t.getName())
                        .isNull(TestTaskEntity::getDeletedAt));
        return same != null && same > 0;
    }
}