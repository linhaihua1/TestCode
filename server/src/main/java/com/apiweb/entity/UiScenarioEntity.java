package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * UI 执行场景（即 UI 自动化测试执行任务）。
 * <p>收集多个 UI 用例按 {@link UiScenarioStepEntity#getSortOrder()} 升序依次执行。</p>
 * <p>执行参数：执行环境（environmentId）、执行参数（variables JSON）、执行机地址（executorUrl/Host/Port）、
 * 失败重试（retryCount）与超时（timeoutMs）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ui_scenario")
public class UiScenarioEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String description;

    /** 执行环境 ID（来自 t_environment 表，执行时合并其变量） */
    private String environmentId;
    /** 任务级执行参数 JSON：[{key,value}]，优先于环境变量 */
    private String variables;
    /** 执行机完整地址（如 http://192.168.1.10:9515），可选 */
    private String executorUrl;
    /** 执行机域名 / IP（如 selenium-hub.example.com 或 192.168.1.10） */
    private String executorHost;
    /** 执行机端口（如 9515 / 4444） */
    private Integer executorPort;
    /** 用例失败后重试次数 */
    private Integer retryCount;
    /** 用例执行超时时间（毫秒） */
    private Integer timeoutMs;
}
