package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.recyclebin.RecycleBinBatchReq;
import com.apiweb.dto.recyclebin.RecycleBinConfigReq;
import com.apiweb.service.RecycleBinService;
import com.apiweb.vo.recyclebin.RecycleBinConfigVO;
import com.apiweb.vo.recyclebin.RecycleBinItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统一回收站（需求文档 §2.3）。
 *
 * <p>覆盖 MODULE / API / CASE / TASK 四类回收对象，支持列表查询、批量还原、
 * 批量永久删除、配置自动清理周期。</p>
 */
@RestController
@RequestMapping("/api/v1/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    // ---------------- 列表 ----------------

    /**
     * 查询回收站列表（按类型）。
     */
    @GetMapping
    public Result<List<RecycleBinItemVO>> list(@RequestParam String projectId,
                                               @RequestParam(defaultValue = "CASE") String type) {
        return Result.ok(recycleBinService.list(projectId, type));
    }

    // ---------------- 还原 ----------------

    /**
     * 批量还原。
     *
     * @return restoredCount=成功还原数；若存在同名冲突则额外返回 renamedIds 与 message
     * @audit 写审计日志：action=restore, entityType=recycle_bin
     */
    @AuditLog(action = "restore", entityType = "recycle_bin")
    @PostMapping("/restore")
    public Result<Map<String, Object>> restore(@Valid @RequestBody RecycleBinBatchReq req) {
        return Result.ok(recycleBinService.restore(req));
    }

    // ---------------- 永久删除 ----------------

    /**
     * 批量永久删除（不可恢复）。
     *
     * @audit 写审计日志：action=permanent_delete, entityType=recycle_bin
     */
    @AuditLog(action = "permanent_delete", entityType = "recycle_bin")
    @DeleteMapping("/permanent")
    public Result<Void> permanent(@Valid @RequestBody RecycleBinBatchReq req) {
        recycleBinService.permanent(req);
        return Result.ok();
    }

    /**
     * 清空回收站（仅清空指定类型且超过 N 天的项）。
     *
     * @audit 写审计日志：action=purge_all, entityType=recycle_bin
     */
    @AuditLog(action = "purge_all", entityType = "recycle_bin")
    @DeleteMapping("/purge")
    public Result<Integer> purgeOld(@RequestParam String projectId,
                                    @RequestParam(defaultValue = "CASE") String type,
                                    @RequestParam(defaultValue = "0") int olderThanDays) {
        return Result.ok(recycleBinService.purgeOld(projectId, type, olderThanDays));
    }

    // ---------------- 自动清理配置 ----------------

    /**
     * 获取项目的回收站自动清理配置。
     */
    @GetMapping("/config")
    public Result<RecycleBinConfigVO> getConfig(@RequestParam String projectId) {
        return Result.ok(recycleBinService.getConfig(projectId));
    }

    /**
     * 配置自动清理周期。
     *
     * @audit 写审计日志：action=update, entityType=recycle_bin_config
     */
    @AuditLog(action = "update", entityType = "recycle_bin_config")
    @PutMapping("/config")
    public Result<Void> updateConfig(@Valid @RequestBody RecycleBinConfigReq req) {
        recycleBinService.updateConfig(req);
        return Result.ok();
    }
}
