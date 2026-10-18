package com.apiweb.converter;

import com.apiweb.entity.ScenarioEntity;
import com.apiweb.entity.ScenarioStepEntity;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.scenario.ScenarioVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
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

    /** 场景步骤转换完成后，把 assertions/extracts 的 JSON 字符串解析为结构化数组 */
    @AfterMapping
    default void parseStepJsonFields(@MappingTarget ScenarioVO.ScenarioStepVO vo) {
        if (vo == null) {
            return;
        }
        vo.setAssertions(JsonUtils.parseObject((String) vo.getAssertions()));
        vo.setExtracts(JsonUtils.parseObject((String) vo.getExtracts()));
    }
}
