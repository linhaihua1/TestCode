package com.apiweb.converter;

import com.apiweb.dto.perf.PerfCaseReq;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.PerfReportEntity;
import com.apiweb.vo.perf.PerfCaseVO;
import com.apiweb.vo.perf.PerfReportVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Perf 实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
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

    /** Report Entity → Report VO */
    PerfReportVO toReportVO(PerfReportEntity entity);

    /** Report Entity 列表 → Report VO 列表 */
    List<PerfReportVO> toReportVOList(List<PerfReportEntity> entities);
}
