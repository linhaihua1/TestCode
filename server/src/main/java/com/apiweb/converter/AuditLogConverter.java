package com.apiweb.converter;

import com.apiweb.entity.AuditLogEntity;
import com.apiweb.vo.auditlog.AuditLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * AuditLog 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface AuditLogConverter {

    AuditLogConverter INSTANCE = Mappers.getMapper(AuditLogConverter.class);

    /** Entity → VO */
    AuditLogVO toVO(AuditLogEntity entity);

    /** Entity 列表 → VO 列表 */
    List<AuditLogVO> toVOList(List<AuditLogEntity> entities);
}
