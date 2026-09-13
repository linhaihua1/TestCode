package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 性能测试用例
 *
 * <p>对应表：{@code t_perf_case}。
 * 性能测试用例，执行时由 PerfExecutionService 转换成 JMeter 的 .jmx 测试计划。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_perf_case")
public class PerfCaseEntity extends BaseEntity {
    /** 所属项目 ID */
    private String projectId;
    /** 名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 并发线程数 */
    private Integer threads;
    /** 加压时长（秒）：线程在该时间内逐步启动 */
    private Integer rampUp;
    /** 循环次数（配置了 duration 时由持续时间控制） */
    private Integer loops;
    /** 耗时（毫秒） */
    private Integer duration;
    /** 思考时间（毫秒）：每个请求之间的固定停顿 */
    private Integer thinkTime;
    /** continue / startnext / stopthread / stoptest */
    private String onSampleError;
    /** JSON: 用户自定义变量 */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String variables;
    /** JSON: HTTP 请求步骤 */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String steps;
    /** JSON: {loadProfile, stepping?, concurrency?} */
    /** 压测配置（JSON：loadProfile 等） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String profile;
    /** 软删除时间：null 表示未删除 */
    private Instant deletedAt;
}
