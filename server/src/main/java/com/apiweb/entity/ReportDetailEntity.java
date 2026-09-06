package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 接口测试报告明细 */
@Data
@TableName("t_report_detail")
public class ReportDetailEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String reportId;
    private String stepName;
    private String status;
    private String error;
    private String assertions;
    private String extracts;
}
