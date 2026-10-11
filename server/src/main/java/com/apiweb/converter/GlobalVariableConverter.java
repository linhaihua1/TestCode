package com.apiweb.converter;

import com.apiweb.entity.GlobalVariableEntity;
import com.apiweb.vo.globalvariable.GlobalVariableVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * GlobalVariable 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface GlobalVariableConverter {

    GlobalVariableConverter INSTANCE = Mappers.getMapper(GlobalVariableConverter.class);

    /** Entity → VO */
    GlobalVariableVO toVO(GlobalVariableEntity entity);

    /** Entity 列表 → VO 列表 */
    List<GlobalVariableVO> toVOList(List<GlobalVariableEntity> entities);
}
