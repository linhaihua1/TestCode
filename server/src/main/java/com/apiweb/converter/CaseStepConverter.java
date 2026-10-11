package com.apiweb.converter;

import com.apiweb.dto.casestep.CaseStepReq;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.vo.casestep.CaseStepVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * CaseStep 实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface CaseStepConverter {

    CaseStepConverter INSTANCE = Mappers.getMapper(CaseStepConverter.class);

    /** Req → Entity */
    CaseStepEntity toEntity(CaseStepReq req);

    /** Entity → VO */
    CaseStepVO toVO(CaseStepEntity entity);

    /** Entity 列表 → VO 列表 */
    List<CaseStepVO> toVOList(List<CaseStepEntity> entities);
}
