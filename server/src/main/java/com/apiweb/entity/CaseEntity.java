package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 测试用例
 *
 * <p>对应表：{@code t_case}。
 * 一条用例 = 若干步骤（steps JSON）的集合，步骤也可拆到 t_case_step 表独立管理。
 * 状态流转：draft（草稿）→ reviewing（评审中）→ pass / fail；删除走软删除（deleted_at）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_case")
public class CaseEntity extends BaseEntity {
    /** 所属项目 ID */
    private String projectId;
    /** 所属模块 ID（可空，表示未归类） */
    private String moduleId;
    /** 名称 */
    private String name;
    /** 描述 */
    private String description;
    /** draft / reviewing / pass / fail / trash */
    private String status;
    /** 优先级 */
    private String priority;
    /** JSON: string[] */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String tags;
    /** JSON: 多步骤定义（每步引用接口+断言+提取+控制器） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String steps;
    /** 版本号（每次保存自增） */
    private Integer version;
    /** 排序号（升序） */
    private Integer sortOrder;
    /**
     * 软删除时间：null 表示未删除。
     *
     * <p>必须显式声明 updateStrategy=ALWAYS：MyBatis-Plus 默认策略为 NOT_NULL，
     * updateById 会忽略 null 值字段，导致「回收站还原」时 setDeletedAt(null) 无法落库。</p>
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant deletedAt;
}
