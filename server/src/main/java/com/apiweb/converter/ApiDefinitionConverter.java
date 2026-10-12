package com.apiweb.converter;

import com.apiweb.dto.apidefinition.ApiDefinitionReq;
import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.util.JsonUtils;
import com.apiweb.vo.apidefinition.ApiDefinitionVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * ApiDefinition 实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>实体中 JSON 字符串字段（pathParams/query/headers/bodySchema/responseExamples/
 * responseHeaders/tags/mockRules）在 VO 中以结构化对象输出，见 {@link #parseJsonFields}。</p>
 */
@Mapper(componentModel = "spring")
public interface ApiDefinitionConverter {

    /** Req → Entity */
    ApiDefinitionEntity toEntity(ApiDefinitionReq req);

    /** Entity → VO */
    ApiDefinitionVO toVO(ApiDefinitionEntity entity);

    /** Entity 列表 → VO 列表 */
    List<ApiDefinitionVO> toVOList(List<ApiDefinitionEntity> entities);

    /** 转换完成后，把 JSON 字符串字段解析为结构化对象 */
    @AfterMapping
    default void parseJsonFields(@MappingTarget ApiDefinitionVO vo) {
        if (vo == null) {
            return;
        }
        vo.setPathParams(JsonUtils.parseObject((String) vo.getPathParams()));
        vo.setQuery(JsonUtils.parseObject((String) vo.getQuery()));
        vo.setHeaders(JsonUtils.parseObject((String) vo.getHeaders()));
        vo.setBodySchema(JsonUtils.parseObject((String) vo.getBodySchema()));
        vo.setResponseExamples(JsonUtils.parseObject((String) vo.getResponseExamples()));
        vo.setResponseHeaders(JsonUtils.parseObject((String) vo.getResponseHeaders()));
        vo.setTags(JsonUtils.parseObject((String) vo.getTags()));
        vo.setMockRules(JsonUtils.parseObject((String) vo.getMockRules()));
    }
}
