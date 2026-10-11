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
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Case 相关实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 */
@Mapper(componentModel = "spring")
public interface CaseConverter {

    /** Req → Entity */
    CaseEntity toEntity(CaseReq req);

    /** Entity → VO */
    CaseVO toVO(CaseEntity entity);

    /** Entity 列表 → VO 列表 */
    List<CaseVO> toVOList(List<CaseEntity> entities);

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
}
