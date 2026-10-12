package com.apiweb.converter;

import com.apiweb.entity.EnvironmentEntity;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.environment.EnvironmentVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
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

    /** 转换完成后，把 JSON 字符串字段解析为结构化对象（variables/headers） */
    @AfterMapping
    default void parseJsonFields(@MappingTarget EnvironmentVO vo) {
        if (vo == null) {
            return;
        }
        vo.setVariables(JsonUtils.parseObject((String) vo.getVariables()));
        vo.setHeaders(JsonUtils.parseObject((String) vo.getHeaders()));
    }
}
