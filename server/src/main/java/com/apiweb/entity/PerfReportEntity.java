package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 性能测试报告
 *
 * <p>对应表：{@code t_perf_report}。
 * 一次性能测试的结果报告；summary 为汇总指标，series 为时序点，labels 为分接口统计。</p>
 */
@Data
@TableName("t_perf_report")
public class PerfReportEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属项目 ID */
    private String projectId;
    /** 所属用例 ID */
    private String caseId;
    /** 名称 */
    private String name;
    /** success / failed / error */
    private String status;
    /** 耗时（毫秒） */
    private Integer duration;
    /** 开始执行时间 */
    private Instant startedAt;
    /** JSON: 汇总指标 */
    private String summary;
    /** JSON: 时序数据 */
    private String series;
    /** JSON: 分接口统计 */
    private String labels;
    /** JSON: 错误 TOP */
    private String errors;
    /** 备注信息（执行失败时记录异常原因） */
    private String message;
}
