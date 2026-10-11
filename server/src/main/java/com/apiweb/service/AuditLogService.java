package com.apiweb.service;

import com.apiweb.common.PageResult;
import com.apiweb.converter.AuditLogConverter;
import com.apiweb.entity.AuditLogEntity;
import com.apiweb.mapper.AuditLogMapper;
import com.apiweb.vo.auditlog.AuditLogVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计日志查询服务（仅管理员）。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final AuditLogConverter auditLogConverter;

    /**
     * 分页查询审计日志（按操作人、实体类型过滤，创建时间倒序）。
     */
    public PageResult<AuditLogVO> page(String username, String entityType, long page, long size) {
        Page<AuditLogEntity> result = auditLogMapper.selectPage(
                new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<AuditLogEntity>()
                        .eq(username != null && !username.isBlank(), AuditLogEntity::getUsername, username)
                        .eq(entityType != null && !entityType.isBlank(),
                                AuditLogEntity::getEntityType, entityType)
                        .orderByDesc(AuditLogEntity::getCreatedAt));
        List<AuditLogVO> records = auditLogConverter.toVOList(result.getRecords());
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }
}
