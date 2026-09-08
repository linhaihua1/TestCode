package com.apiweb.controller;

import com.apiweb.common.Result;
import com.apiweb.entity.AuditLogEntity;
import com.apiweb.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审计日志查询（仅管理员）。
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogMapper auditLogMapper;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<AuditLogEntity> result = auditLogMapper.selectPage(
                new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<AuditLogEntity>()
                        .eq(username != null && !username.isBlank(), AuditLogEntity::getUsername, username)
                        .eq(entityType != null && !entityType.isBlank(),
                                AuditLogEntity::getEntityType, entityType)
                        .orderByDesc(AuditLogEntity::getCreatedAt));
        return Result.ok(Map.of(
                "records", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "size", result.getSize()));
    }
}
