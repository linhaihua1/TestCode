package com.apiweb.converter;

import com.apiweb.entity.ScenarioEntity;
import com.apiweb.entity.ScenarioStepEntity;
import com.apiweb.vo.scenario.ScenarioVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Scenario 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface ScenarioConverter {

    ScenarioConverter INSTANCE = Mappers.getMapper(ScenarioConverter.class);

    /** Entity → VO */
    ScenarioVO toVO(ScenarioEntity entity);

    /** Entity 列表 → VO 列表 */
    List<ScenarioVO> toVOList(List<ScenarioEntity> entities);

    /** Step Entity → Step VO */
    ScenarioVO.ScenarioStepVO toStepVO(ScenarioStepEntity entity);

    /** Step Entity 列表 → Step VO 列表 */
    List<ScenarioVO.ScenarioStepVO> toStepVOList(List<ScenarioStepEntity> entities);
}
