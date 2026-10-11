package com.apiweb.converter;

import com.apiweb.entity.ProjectEntity;
import com.apiweb.vo.project.ProjectVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Project 实体 ↔ VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface ProjectConverter {

    ProjectConverter INSTANCE = Mappers.getMapper(ProjectConverter.class);

    ProjectVO toVO(ProjectEntity entity);

    List<ProjectVO> toVOList(List<ProjectEntity> entities);
}
