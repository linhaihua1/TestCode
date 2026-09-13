package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用例版本快照
 *
 * <p>对应表：{@code t_case_version}。
 * 每次用例保存时生成一条快照（snapshot JSON），支持版本对比与回滚。</p>
 */
@Data
@TableName("t_case_version")
public class CaseVersionEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属用例 ID */
    private String caseId;
    /** 版本号（每次保存自增） */
    private Integer version;
    /** JSON: 用例完整快照 */
    private String snapshot;
    /** 本次变更说明 */
    private String changeSummary;
    /** 创建人 */
    private String createdBy;
    /** 创建时间（UTC） */
    private Instant createdAt;
}
