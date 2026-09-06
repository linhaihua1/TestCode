package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** 测试用例 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_case")
public class CaseEntity extends BaseEntity {
    private String projectId;
    private String moduleId;
    private String name;
    private String description;
    /** draft / reviewing / pass / fail / trash */
    private String status;
    private String priority;
    /** JSON: string[] */
    private String tags;
    /** JSON: 多步骤定义（每步引用接口+断言+提取+控制器） */
    private String steps;
    private Integer version;
    private Integer sortOrder;
    private Instant deletedAt;
}
