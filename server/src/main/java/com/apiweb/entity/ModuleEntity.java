package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 模块树节点（多级）
 *
 * <p>对应表：{@code t_module}。
 * 用例/接口/UI/性能四类资产共用的树形目录节点，parentId 为 null 表示根节点。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_module")
public class ModuleEntity extends BaseEntity {
    /** 所属项目 ID */
    private String projectId;
    /** 父节点 ID（null 表示顶层） */
    private String parentId;
    /** 名称 */
    private String name;
    /** case / api / ui / perf */
    private String type;
    /** 排序号（升序） */
    private Integer sortOrder;

    /** 软删除时间：null 表示未删除，进入回收站时设置 */
    /** 软删除时间：null 表示未删除 */
    @TableField("deleted_at")
    private Instant deletedAt;
}
