package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 回收站自动清理配置（每项目一条）。
 *
 * <p>需求文档 §2.3.2：可配置自动清理周期 7/15/30/60 天或不自动清理，
 * 到期数据在每日凌晨定时任务中自动清除。
 *
 * <p>{@code cleanupDays == 0} 表示不自动清理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recycle_bin_config")
public class RecycleBinConfigEntity extends BaseEntity {
    /** 项目 ID */
    private String projectId;
    /** 自动清理天数；0 = 不自动清理 */
    private Integer cleanupDays;
}