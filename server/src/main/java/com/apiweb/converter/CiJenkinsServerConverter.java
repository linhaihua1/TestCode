package com.apiweb.converter;

import com.apiweb.entity.CiJenkinsServerEntity;
import com.apiweb.vo.cijenkins.CiJenkinsServerVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * CiJenkinsServer 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>{@code CiJenkinsServerVO} 不含 apiToken，MapStruct 自动不映射，杜绝 Token 外泄。</p>
 */
@Mapper(componentModel = "spring")
public interface CiJenkinsServerConverter {

    CiJenkinsServerConverter INSTANCE = Mappers.getMapper(CiJenkinsServerConverter.class);

    /** Entity → VO */
    CiJenkinsServerVO toVO(CiJenkinsServerEntity entity);

    /** Entity 列表 → VO 列表 */
    List<CiJenkinsServerVO> toVOList(List<CiJenkinsServerEntity> entities);
}
