package com.apiweb.converter;

import com.apiweb.dto.perf.PerfCaseReq;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.PerfReportEntity;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.perf.PerfCaseVO;
import com.apiweb.vo.perf.PerfReportVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Perf 实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>实体中 {@code variables/steps/profile} 以 JSON 字符串存储，VO 中以结构化对象输出，
 * 因此在 {@link #toCaseVO(PerfCaseEntity)} 完成后统一做 JSON 解析（见 {@link #parseJsonFields}）。</p>
 */
@Mapper(componentModel = "spring")
public interface PerfConverter {

    PerfConverter INSTANCE = Mappers.getMapper(PerfConverter.class);

    /** Case Req → Case Entity */
    PerfCaseEntity toEntity(PerfCaseReq req);

    /** Case Entity → Case VO */
    PerfCaseVO toCaseVO(PerfCaseEntity entity);

    /** Case Entity 列表 → Case VO 列表 */
    List<PerfCaseVO> toCaseVOList(List<PerfCaseEntity> entities);

    /** 转换完成后，把 JSON 字符串字段解析为结构化对象（steps/variables/profile） */
    @AfterMapping
    default void parseJsonFields(@MappingTarget PerfCaseVO vo) {
        if (vo == null) {
            return;
        }
        vo.setSteps(JsonUtils.parseObject((String) vo.getSteps()));
        vo.setVariables(JsonUtils.parseObject((String) vo.getVariables()));
        vo.setProfile(JsonUtils.parseObject((String) vo.getProfile()));
    }

    /** Report Entity → Report VO */
    PerfReportVO toReportVO(PerfReportEntity entity);

    /** Report Entity 列表 → Report VO 列表 */
    List<PerfReportVO> toReportVOList(List<PerfReportEntity> entities);
}
