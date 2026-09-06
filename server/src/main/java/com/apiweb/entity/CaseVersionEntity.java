package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 用例版本快照 */
@Data
@TableName("t_case_version")
public class CaseVersionEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String caseId;
    private Integer version;
    /** JSON: 用例完整快照 */
    private String snapshot;
    private String changeSummary;
    private String createdBy;
    private Instant createdAt;
}
