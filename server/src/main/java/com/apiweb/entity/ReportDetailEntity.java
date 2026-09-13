package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 接口测试报告明细
 *
 * <p>对应表：{@code t_report_detail}。
 * 报告的步骤级明细；报告体量大时整包明细会转存对象存储（见 ReportEntity 的引用字段）。</p>
 */
@Data
@TableName("t_report_detail")
public class ReportDetailEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属报告 ID */
    private String reportId;
    /** 步骤名称 */
    private String stepName;
    /** 状态 */
    private String status;
    /** 错误信息 */
    private String error;
    /** 断言配置（JSON） */
    private String assertions;
    /** 变量提取配置（JSON） */
    private String extracts;
}
