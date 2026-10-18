package com.apiweb.converter;

import com.apiweb.entity.ReportDetailEntity;
import com.apiweb.entity.ReportEntity;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.report.ReportDetailVO;
import com.apiweb.vo.report.ReportVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Report 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>明细中的 assertions/extracts 以 JSON 字符串存储，VO 输出结构化对象
 * （见 {@link #parseDetailJsonFields}），否则前端按数组绑定会异常。</p>
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

    /** 明细转换完成后，把 assertions/extracts 的 JSON 字符串解析为结构化对象 */
    @AfterMapping
    default void parseDetailJsonFields(@MappingTarget ReportDetailVO vo) {
        if (vo == null) {
            return;
        }
        vo.setAssertions(JsonUtils.parseObject((String) vo.getAssertions()));
        vo.setExtracts(JsonUtils.parseObject((String) vo.getExtracts()));
    }
}
