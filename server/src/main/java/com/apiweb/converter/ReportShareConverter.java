package com.apiweb.converter;

import com.apiweb.entity.ReportShareEntity;
import com.apiweb.vo.report.ReportShareVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * ReportShare 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>{@code ReportShareVO} 不含 password/allowedIps，MapStruct 自动不映射，
 * 从源头杜绝敏感字段外泄。</p>
 */
@Mapper(componentModel = "spring")
public interface ReportShareConverter {

    ReportShareConverter INSTANCE = Mappers.getMapper(ReportShareConverter.class);

    /** Entity → VO */
    ReportShareVO toVO(ReportShareEntity entity);

    /** Entity 列表 → VO 列表 */
    List<ReportShareVO> toVOList(List<ReportShareEntity> entities);
}
