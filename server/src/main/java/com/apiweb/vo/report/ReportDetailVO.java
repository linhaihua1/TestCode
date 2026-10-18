package com.apiweb.vo.report;

import lombok.Data;

/**
 * 报告明细 VO（响应）。
 *
 * <p>assertions/extracts 在实体里以 JSON 字符串存储，本 VO 对外输出结构化对象，
 * 由 {@code ReportConverter#parseDetailJsonFields} 解析；前端按数组绑定表格与断言统计。</p>
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
    /** 断言执行结果：[{source, operator, expected, actual, passed}] */
    private Object assertions;
    /** 变量提取结果：[{variable, value}] */
    private Object extracts;
}
