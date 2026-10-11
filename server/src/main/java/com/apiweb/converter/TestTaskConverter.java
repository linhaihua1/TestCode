package com.apiweb.converter;

import com.apiweb.dto.testtask.TestTaskReq;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.vo.testtask.TestTaskVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * TestTask 实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface TestTaskConverter {

    TestTaskConverter INSTANCE = Mappers.getMapper(TestTaskConverter.class);

    /** Req → Entity */
    TestTaskEntity toEntity(TestTaskReq req);

    /** Entity → VO */
    TestTaskVO toVO(TestTaskEntity entity);

    /** Entity 列表 → VO 列表 */
    List<TestTaskVO> toVOList(List<TestTaskEntity> entities);
}
