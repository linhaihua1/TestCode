package com.apiweb.converter;

import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.vo.report.ReportDetailVO;
import com.apiweb.vo.report.ReportVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Report 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface ReportConverter {

    ReportConverter INSTANCE = Mappers.getMapper(ReportConverter.class);

    /** Entity → VO */
    ReportVO toVO(ReportEntity entity);

    /** Entity 列表 → VO 列表 */
    List<ReportVO> toVOList(List<ReportEntity> entities);

    /** Detail Entity → Detail VO */
    ReportDetailVO toDetailVO(ReportDetailEntity entity);

    /** Detail Entity 列表 → Detail VO 列表 */
    List<ReportDetailVO> toDetailVOList(List<ReportDetailEntity> entities);
}
