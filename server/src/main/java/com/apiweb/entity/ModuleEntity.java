package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** 模块树节点（多级） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_module")
public class ModuleEntity extends BaseEntity {
    private String projectId;
    private String parentId;
    private String name;
    /** case / api / ui / perf */
    private String type;
    private Integer sortOrder;

    /** 软删除时间：null 表示未删除，进入回收站时设置 */
    @TableField("deleted_at")
    private Instant deletedAt;
}
