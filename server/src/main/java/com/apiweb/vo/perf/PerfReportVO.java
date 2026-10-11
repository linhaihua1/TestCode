package com.apiweb.vo.perf;

import lombok.Data;

import java.time.Instant;

/**
 * 性能测试报告 VO（响应）。
 */
@Data
public class PerfReportVO {

    private String id;
    private String projectId;
    private String caseId;
    private String name;
    private String status;
    private Integer duration;
    private Instant startedAt;
    /** JSON: 汇总指标 */
    private String summary;
    /** JSON: 时序数据 */
    private String series;
    /** JSON: 分接口统计 */
    private String labels;
    /** JSON: 错误 TOP */
    private String errors;
    /** 备注信息 */
    private String message;
}
