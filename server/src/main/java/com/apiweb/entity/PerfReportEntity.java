package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 性能测试报告 */
@Data
@TableName("t_perf_report")
public class PerfReportEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String caseId;
    private String name;
    /** success / failed / error */
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
    private String message;
}
