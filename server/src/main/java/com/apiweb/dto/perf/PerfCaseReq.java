package com.apiweb.dto.perf;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 性能用例请求 DTO。
 */
@Data
public class PerfCaseReq {

    /** 所属项目 ID（必填） */
    @NotBlank(message = "projectId 必填")
    private String projectId;

    /** 名称（必填） */
    @NotBlank(message = "用例名称必填")
    @Size(max = 200)
    private String name;

    /** 描述 */
    @Size(max = 2000)
    private String description;

    /** 并发线程数 */
    private Integer threads;
    /** 加压时长（秒） */
    private Integer rampUp;
    /** 循环次数 */
    private Integer loops;
    /** 耗时（毫秒） */
    private Integer duration;
    /** 思考时间（毫秒） */
    private Integer thinkTime;
    /** continue / startnext / stopthread / stoptest */
    private String onSampleError;
    /** JSON: 用户自定义变量 */
    private String variables;
    /** JSON: HTTP 请求步骤 */
    private String steps;
    /** JSON: 压测配置 */
    private String profile;
}
