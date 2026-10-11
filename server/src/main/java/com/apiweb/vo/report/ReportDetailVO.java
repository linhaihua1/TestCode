package com.apiweb.vo.report;

import lombok.Data;

/**
 * 报告明细 VO（响应）。
 */
@Data
public class ReportDetailVO {

    /** 明细 ID */
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
