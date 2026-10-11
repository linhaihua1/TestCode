package com.apiweb.converter;

import com.apiweb.entity.EnvironmentEntity;
import com.apiweb.vo.environment.EnvironmentVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Environment 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface EnvironmentConverter {

    EnvironmentConverter INSTANCE = Mappers.getMapper(EnvironmentConverter.class);

    /** Entity → VO */
    EnvironmentVO toVO(EnvironmentEntity entity);

    /** Entity 列表 → VO 列表 */
    List<EnvironmentVO> toVOList(List<EnvironmentEntity> entities);
}
