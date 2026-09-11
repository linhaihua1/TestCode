package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** 性能测试用例 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_perf_case")
public class PerfCaseEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String description;
    private Integer threads;
    private Integer rampUp;
    private Integer loops;
    private Integer duration;
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
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String profile;
    private Instant deletedAt;
}
