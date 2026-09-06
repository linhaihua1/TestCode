package com.apiweb.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.Instant;

/**
 * 实体基类：UUID 主键 + 创建/更新时间（UTC）自动填充。
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
