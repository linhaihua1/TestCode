package com.apiweb.converter;

import com.apiweb.dto.ui.UiTestCaseReq;
import com.apiweb.entity.UiReportEntity;
import com.apiweb.entity.UiScenarioEntity;
import com.apiweb.entity.UiScenarioStepEntity;
import com.apiweb.entity.UiTestCaseEntity;
import com.apiweb.vo.ui.UiReportVO;
import com.apiweb.vo.ui.UiScenarioStepVO;
import com.apiweb.vo.ui.UiScenarioVO;
import com.apiweb.vo.ui.UiTestCaseVO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * UI 相关实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>注意：UiScenarioReq 的 variables 为 Map 而实体为 String，需手动 JSON 转换，
 * 故场景的 Req→Entity 转换在 Service 层手工完成，此处仅提供 Entity→VO 映射。</p>
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

    /** UiScenarioStep Entity → VO */
    UiScenarioStepVO toScenarioStepVO(UiScenarioStepEntity entity);

    /** UiScenarioStep Entity 列表 → VO 列表 */
    List<UiScenarioStepVO> toScenarioStepVOList(List<UiScenarioStepEntity> entities);

    /** UiReport Entity → VO */
    UiReportVO toReportVO(UiReportEntity entity);

    /** UiReport Entity 列表 → VO 列表 */
    List<UiReportVO> toReportVOList(List<UiReportEntity> entities);
}
