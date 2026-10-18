package com.apiweb.converter;

import com.apiweb.dto.casee.CaseReq;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseReviewEntity;
import com.apiweb.entity.CaseVersionEntity;
import com.apiweb.entity.DebugRecordEntity;
import com.apiweb.vo.casee.CaseReviewVO;
import com.apiweb.vo.casee.CaseVO;
import com.apiweb.vo.casee.CaseVersionVO;
import com.apiweb.vo.casee.DebugRecordVO;
import com.apiweb.util.JsonUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Case 相关实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>实体中 {@code tags/steps} 以 JSON 字符串存储，VO 中以结构化对象输出，
 * 因此在 {@link #toVO(CaseEntity)} 完成后统一做 JSON 解析（见 {@link #parseJsonFields}）。</p>
 */
@Mapper(componentModel = "spring")
public interface CaseConverter {

    /** Req → Entity */
    CaseEntity toEntity(CaseReq req);

    /** Entity → VO */
    CaseVO toVO(CaseEntity entity);

    /** Entity 列表 → VO 列表 */
    List<CaseVO> toVOList(List<CaseEntity> entities);

    /** 转换完成后，把 JSON 字符串字段解析为结构化对象（tags/steps） */
    @AfterMapping
    default void parseJsonFields(@MappingTarget CaseVO vo) {
        if (vo == null) {
            return;
        }
        vo.setTags(JsonUtils.parseObject((String) vo.getTags()));
        vo.setSteps(JsonUtils.parseObject((String) vo.getSteps()));
    }

    /** CaseReview Entity → VO */
    CaseReviewVO toReviewVO(CaseReviewEntity entity);

    /** CaseReview Entity 列表 → VO 列表 */
    List<CaseReviewVO> toReviewVOList(List<CaseReviewEntity> entities);

    /** CaseVersion Entity → VO */
    CaseVersionVO toVersionVO(CaseVersionEntity entity);

    /** CaseVersion Entity 列表 → VO 列表 */
    List<CaseVersionVO> toVersionVOList(List<CaseVersionEntity> entities);

    /** DebugRecord Entity → VO */
    DebugRecordVO toDebugRecordVO(DebugRecordEntity entity);

    /** DebugRecord Entity 列表 → VO 列表 */
    List<DebugRecordVO> toDebugRecordVOList(List<DebugRecordEntity> entities);

    /**
     * 调试记录转换完成后，把 JSON 字符串字段解析为结构化对象。
     *
     * <p>前端调试记录面板对 stepResults/assertionResults 做数组遍历、
     * extractedVariables 做对象遍历，输出字符串会逐字符渲染。</p>
     */
    @AfterMapping
    default void parseDebugRecordJsonFields(@MappingTarget DebugRecordVO vo) {
        if (vo == null) {
            return;
        }
        vo.setAssertionResults(JsonUtils.parseObject((String) vo.getAssertionResults()));
        vo.setExtractedVariables(JsonUtils.parseObject((String) vo.getExtractedVariables()));
        vo.setStepResults(JsonUtils.parseObject((String) vo.getStepResults()));
    }
}
