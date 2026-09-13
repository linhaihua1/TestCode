package com.apiweb.controller;

import com.apiweb.common.Result;
import com.apiweb.entity.AuditLogEntity;
import com.apiweb.mapper.AuditLogMapper;
import com.apiweb.security.RequireRole;
import com.apiweb.security.Role;
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
@RequireRole(Role.ADMIN_CODE)
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogMapper auditLogMapper;

    /**
     * 分页查询审计日志（仅 ADMIN 角色可访问，见类级 @RequireRole）。
     * <p>支持按操作人用户名、操作对象类型（entityType）过滤，按创建时间倒序。
     *
     * @param username   操作人用户名（可选），精确匹配
     * @param entityType 操作对象类型（可选），如 api/project/case 等
     * @param page        页码，默认 1
     * @param size        每页条数，默认 20，上限 100（防止大页查询）
     * @return records=本页日志列表，total=总条数，page/size=分页参数回显
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<AuditLogEntity> result = auditLogMapper.selectPage(
                // 单页上限 100，防止超大 size 拖垮查询
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
