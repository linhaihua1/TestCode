package com.apiweb.converter;

import com.apiweb.dto.apidefinition.ApiDefinitionReq;
import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.vo.apidefinition.ApiDefinitionVO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * ApiDefinition 实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface ApiDefinitionConverter {

    /** Req → Entity */
    ApiDefinitionEntity toEntity(ApiDefinitionReq req);

    /** Entity → VO */
    ApiDefinitionVO toVO(ApiDefinitionEntity entity);

    /** Entity 列表 → VO 列表 */
    List<ApiDefinitionVO> toVOList(List<ApiDefinitionEntity> entities);
}
