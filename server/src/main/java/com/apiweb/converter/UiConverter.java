package com.apiweb.converter;

import com.apiweb.dto.ui.UiTestCaseReq;
import com.apiweb.entity.UiReportEntity;
import com.apiweb.entity.UiScenarioEntity;
import com.apiweb.entity.UiScenarioStepEntity;
import com.apiweb.entity.UiTestCaseEntity;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.ui.UiReportVO;
import com.apiweb.vo.ui.UiScenarioStepVO;
import com.apiweb.vo.ui.UiScenarioVO;
import com.apiweb.vo.ui.UiTestCaseVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * UI 相关实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>注意：UiScenarioReq 的 variables 为 Map 而实体为 String，需手动 JSON 转换，
 * 故场景的 Req→Entity 转换在 Service 层手工完成，此处仅提供 Entity→VO 映射。</p>
 *
 * <p>UI 用例实体的 setupSteps/steps/teardownSteps 与场景实体的 variables 以 JSON 字符串存储，
 * VO 中以结构化对象输出，见 {@link #parseTestCaseJsonFields} / {@link #parseScenarioJsonFields}。</p>
 */
@Mapper(componentModel = "spring")
public interface UiConverter {

    /** Req → Entity */
    UiTestCaseEntity toEntity(UiTestCaseReq req);

    /** Entity → VO */
    UiTestCaseVO toTestCaseVO(UiTestCaseEntity entity);

    /** Entity 列表 → VO 列表 */
    List<UiTestCaseVO> toTestCaseVOList(List<UiTestCaseEntity> entities);

    /** UiScenario Entity → VO */
    UiScenarioVO toScenarioVO(UiScenarioEntity entity);

    /** UiScenario Entity 列表 → VO 列表 */
    List<UiScenarioVO> toScenarioVOList(List<UiScenarioEntity> entities);

    /** UI 用例转换完成后，把 JSON 字符串字段解析为结构化对象 */
    @AfterMapping
    default void parseTestCaseJsonFields(@MappingTarget UiTestCaseVO vo) {
        if (vo == null) {
            return;
        }
        vo.setSetupSteps(JsonUtils.parseObject((String) vo.getSetupSteps()));
        vo.setSteps(JsonUtils.parseObject((String) vo.getSteps()));
        vo.setTeardownSteps(JsonUtils.parseObject((String) vo.getTeardownSteps()));
    }

    /** 场景转换完成后，把 JSON 字符串字段解析为结构化对象 */
    @AfterMapping
    default void parseScenarioJsonFields(@MappingTarget UiScenarioVO vo) {
        if (vo == null) {
            return;
        }
        vo.setVariables(JsonUtils.parseObject((String) vo.getVariables()));
    }

    /** UiScenarioStep Entity → VO */
    UiScenarioStepVO toScenarioStepVO(UiScenarioStepEntity entity);

    /** UiScenarioStep Entity 列表 → VO 列表 */
    List<UiScenarioStepVO> toScenarioStepVOList(List<UiScenarioStepEntity> entities);

    /** UiReport Entity → VO */
    UiReportVO toReportVO(UiReportEntity entity);

    /** UiReport Entity 列表 → VO 列表 */
    List<UiReportVO> toReportVOList(List<UiReportEntity> entities);

    /** UI 报告转换完成后，把 details JSON 字符串解析为结构化对象（数组/错误对象） */
    @AfterMapping
    default void parseReportJsonFields(@MappingTarget UiReportVO vo) {
        if (vo == null) {
            return;
        }
        vo.setDetails(JsonUtils.parseObject((String) vo.getDetails()));
    }
}
