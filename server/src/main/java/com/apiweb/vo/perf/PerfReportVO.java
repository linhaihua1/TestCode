package com.apiweb.vo.perf;

import lombok.Data;

import java.time.Instant;

/**
 * 性能测试报告 VO（响应）。
 *
 * <p>实体中 summary/series/labels/errors 以 JSON 字符串存储，本 VO 对外输出为结构化对象
 * （Map / List），由 {@code PerfConverter#parseReportJsonFields} 在 MapStruct 映射完成后解析。
 * 前端报告页直接把它们当对象/数组使用，输出字符串会导致渲染中断（白屏）。</p>
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
    /** 汇总指标：sampleCount/errorCount/avg/min/max/p90/p95/p99/tps */
    private Object summary;
    /** 时序数据：[{ts, elapsed}] */
    private Object series;
    /** 分接口统计：[{label, count, avg, p90, p95, p99}] */
    private Object labels;
    /** 错误 TOP：[{message, count}] */
    private Object errors;
    /** 备注信息 */
    private String message;
}
