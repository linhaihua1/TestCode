package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.dto.recyclebin.RecycleBinBatchReq;
import com.apiweb.dto.recyclebin.RecycleBinConfigReq;
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
import com.apiweb.vo.recyclebin.RecycleBinConfigVO;
import com.apiweb.vo.recyclebin.RecycleBinItemVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一回收站业务服务。
 *
 * <p>覆盖 MODULE / API / CASE / TASK 四类回收对象，提供列表、单/批量还原、
 * 单/批量永久删除、自动清理配置。</p>
 */
@Service
@RequiredArgsConstructor
public class RecycleBinService {

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

    // ---------------- 列表 ----------------

    /**
     * 查询回收站列表（按类型）。
     */
    public List<RecycleBinItemVO> list(String projectId, String type) {
        return switch (type) {
            case TYPE_MODULE -> buildModuleItems(projectId);
            case TYPE_API -> buildApiItems(projectId);
            case TYPE_TASK -> buildTaskItems(projectId);
            default -> buildCaseItems(projectId);
        };
    }

    private List<RecycleBinItemVO> buildCaseItems(String projectId) {
        List<CaseEntity> list = caseMapper.selectList(
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .isNotNull(CaseEntity::getDeletedAt)
                        .orderByDesc(CaseEntity::getDeletedAt));
        List<RecycleBinItemVO> out = new ArrayList<>();
        for (CaseEntity c : list) {
            RecycleBinItemVO it = new RecycleBinItemVO();
            it.setId(c.getId());
            it.setType(TYPE_CASE);
            it.setName(c.getName());
            it.setExtra("所属目录: " + (c.getModuleId() == null ? "根" : c.getModuleId()));
            it.setDeletedAt(c.getDeletedAt());
            out.add(it);
        }
        return out;
    }

    private List<RecycleBinItemVO> buildModuleItems(String projectId) {
        List<ModuleEntity> list = moduleMapper.selectList(
                new LambdaQueryWrapper<ModuleEntity>()
                        .eq(ModuleEntity::getProjectId, projectId)
                        .isNotNull(ModuleEntity::getDeletedAt)
                        .orderByDesc(ModuleEntity::getDeletedAt));
        List<RecycleBinItemVO> out = new ArrayList<>();
        for (ModuleEntity m : list) {
            RecycleBinItemVO it = new RecycleBinItemVO();
            it.setId(m.getId());
            it.setType(TYPE_MODULE);
            it.setName(m.getName());
            it.setExtra("目录类型: " + m.getType());
            it.setDeletedAt(m.getDeletedAt());
            out.add(it);
        }
        return out;
    }

    private List<RecycleBinItemVO> buildApiItems(String projectId) {
        List<ApiDefinitionEntity> list = apiMapper.selectList(
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .isNotNull(ApiDefinitionEntity::getDeletedAt)
                        .orderByDesc(ApiDefinitionEntity::getDeletedAt));
        List<RecycleBinItemVO> out = new ArrayList<>();
        for (ApiDefinitionEntity a : list) {
            RecycleBinItemVO it = new RecycleBinItemVO();
            it.setId(a.getId());
            it.setType(TYPE_API);
            it.setName(a.getName());
            it.setExtra(a.getMethod() + " " + a.getPath());
            it.setDeletedAt(a.getDeletedAt());
            out.add(it);
        }
        return out;
    }

    private List<RecycleBinItemVO> buildTaskItems(String projectId) {
        List<TestTaskEntity> list = testTaskMapper.selectList(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getProjectId, projectId)
                        .isNotNull(TestTaskEntity::getDeletedAt)
                        .orderByDesc(TestTaskEntity::getDeletedAt));
        List<RecycleBinItemVO> out = new ArrayList<>();
        for (TestTaskEntity t : list) {
            RecycleBinItemVO it = new RecycleBinItemVO();
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
     * 批量还原。
     *
     * @return restoredCount=成功还原数；存在同名冲突时额外返回 renamedIds 与 message
     */
    @Transactional
    public Map<String, Object> restore(RecycleBinBatchReq req) {
        List<String> ids = req.getIds();
        String type = req.getType() == null ? TYPE_CASE : req.getType();
        int restored = 0;
        List<String> renamed = new ArrayList<>();
        switch (type) {
            case TYPE_MODULE -> {
                for (String id : ids) {
                    ModuleEntity m = moduleMapper.selectById(id);
                    if (m != null) {
                        if (m.getParentId() != null && moduleMapper.selectById(m.getParentId()) == null) {
                            m.setParentId(null);
                        }
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
        return result;
    }

    // ---------------- 永久删除 ----------------

    /**
     * 批量永久删除。
     */
    @Transactional
    public void permanent(RecycleBinBatchReq req) {
        List<String> ids = req.getIds();
        String type = req.getType() == null ? TYPE_CASE : req.getType();
        switch (type) {
            case TYPE_MODULE -> ids.forEach(moduleMapper::deleteById);
            case TYPE_API -> ids.forEach(apiMapper::deleteById);
            case TYPE_TASK -> ids.forEach(testTaskMapper::deleteById);
            default -> ids.forEach(caseMapper::deleteById);
        }
    }

    /**
     * 清空回收站（仅清空指定类型且超过 N 天的项）。
     */
    @Transactional
    public int purgeOld(String projectId, String type, int olderThanDays) {
        Instant threshold = olderThanDays > 0
                ? Instant.now().minusSeconds(olderThanDays * 86400L)
                : Instant.now().minusSeconds(86400L);
        return switch (type) {
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
    }

    // ---------------- 自动清理配置 ----------------

    /**
     * 获取项目的回收站自动清理配置（无配置时返回默认 cleanupDays=30）。
     */
    public RecycleBinConfigVO getConfig(String projectId) {
        RecycleBinConfigEntity cfg = configMapper.selectOne(
                new LambdaQueryWrapper<RecycleBinConfigEntity>()
                        .eq(RecycleBinConfigEntity::getProjectId, projectId));
        RecycleBinConfigVO vo = new RecycleBinConfigVO();
        if (cfg == null) {
            vo.setProjectId(projectId);
            vo.setCleanupDays(30);
        } else {
            vo.setProjectId(cfg.getProjectId());
            vo.setCleanupDays(cfg.getCleanupDays());
        }
        return vo;
    }

    /**
     * 配置自动清理周期。cleanupDays=0 表示不自动清理。
     */
    @Transactional
    public void updateConfig(RecycleBinConfigReq req) {
        if (req.getCleanupDays() < 0) {
            throw BizException.badRequest("cleanupDays 必须 >= 0");
        }
        RecycleBinConfigEntity existing = configMapper.selectOne(
                new LambdaQueryWrapper<RecycleBinConfigEntity>()
                        .eq(RecycleBinConfigEntity::getProjectId, req.getProjectId()));
        if (existing == null) {
            RecycleBinConfigEntity cfg = new RecycleBinConfigEntity();
            cfg.setProjectId(req.getProjectId());
            cfg.setCleanupDays(req.getCleanupDays());
            configMapper.insert(cfg);
        } else {
            existing.setCleanupDays(req.getCleanupDays());
            configMapper.updateById(existing);
        }
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
