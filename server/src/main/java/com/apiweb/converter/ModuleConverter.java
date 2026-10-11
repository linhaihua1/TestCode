package com.apiweb.converter;

import com.apiweb.entity.ModuleEntity;
import com.apiweb.vo.module.ModuleVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Module 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface ModuleConverter {

    ModuleConverter INSTANCE = Mappers.getMapper(ModuleConverter.class);

    /** Entity → VO */
    ModuleVO toVO(ModuleEntity entity);

    /** Entity 列表 → VO 列表 */
    List<ModuleVO> toVOList(List<ModuleEntity> entities);
}
