package com.apiweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 执行引擎默认参数配置（取代散落在执行器中的魔法数字）。
 *
 * <p>对应 {@code application.yml}：
 * <pre>
 * apiweb:
 *   engine:
 *     wait-default-ms: 1000            # WAIT 步骤固定等待默认值
 *     wait-dynamic-interval-ms: 500    # WAIT 动态条件轮询间隔
 *     wait-dynamic-timeout-ms: 30000   # WAIT 动态条件最大等待
 *     while-max-timeout-ms: 60000      # WHILE 循环最大时长兜底
 *     while-max-iterations: 1000       # WHILE 循环最大迭代次数（防死循环）
 *     perf-poll-interval-ms: 500       # 性能压测状态轮询间隔
 *     perf-max-duration-hours: 2       # 性能压测最长执行时长
 *     list-max-limit: 100              # 列表查询默认最大条数
 *   ui:
 *     element-timeout-ms: 5000         # 元素等待超时默认值
 *     wait-default-ms: 1000            # UI wait 动作默认时长
 *     min-element-timeout-ms: 1000     # 元素等待超时下限
 *     headless: true                   # 是否无界面运行
 *     window-size: 1920,1080           # 窗口尺寸
 *     page-load-timeout-seconds: 30    # 页面加载超时
 *     implicit-wait-seconds: 3         # 隐式等待
 *     driver-path: ""                  # 浏览器驱动路径（空=自动解析）
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "apiweb.engine")
public class EngineProperties {

    /** WAIT 步骤固定等待默认值（毫秒） */
    private long waitDefaultMs = 1000;

    /** WAIT 动态条件轮询间隔（毫秒） */
    private long waitDynamicIntervalMs = 500;

    /** WAIT 动态条件最大等待（毫秒） */
    private long waitDynamicTimeoutMs = 30_000;

    /** WHILE 循环最大时长兜底（毫秒） */
    private long whileMaxTimeoutMs = 60_000;

    /** WHILE 循环最大迭代次数兜底（防死循环） */
    private int whileMaxIterations = 1000;

    /** 性能压测状态轮询间隔（毫秒） */
    private long perfPollIntervalMs = 500;

    /** 性能压测最长执行时长（小时），超过则强制停止 */
    private long perfMaxDurationHours = 2;

    /** 列表查询默认最大条数 */
    private int listMaxLimit = 100;

    /** 任务并行执行池默认大小 */
    private int defaultParallelPoolSize = 5;

    /** 任务并行执行池上限 */
    private int maxParallelPoolSize = 200;

    /** 任务并行执行池等待队列容量 */
    /** 执行器队列容量 */
    private int executorQueueCapacity = 1024;

    /** 单次 HTTP 请求超时（毫秒），默认 60s，可通过 apiweb.engine.http-timeout-ms 调整 */
    private long httpTimeoutMs = 60_000;

    /** 并行任务整体等待基准超时（秒） */
    private int parallelWaitTimeoutSeconds = 60;

    /** UI 自动化相关默认值 */
    private Ui ui = new Ui();

    /** UI 自动化默认值 */
    @Data
    public static class Ui {
        /** 元素等待超时默认值（毫秒） */
        private long elementTimeoutMs = 5000;
        /** UI wait 动作默认时长（毫秒） */
        private long waitDefaultMs = 1000;
        /** 元素等待超时下限（毫秒） */
        private long minElementTimeoutMs = 1000;
        /** 是否使用 headless 模式（无界面） */
        private boolean headless = true;
        /** 浏览器窗口尺寸 */
        private String windowSize = "1920,1080";
        /** 页面加载超时（秒） */
        private long pageLoadTimeoutSeconds = 30;
        /** 隐式等待（秒） */
        private long implicitWaitSeconds = 3;
        /** 浏览器驱动可执行文件路径（为空时由 Selenium Manager 自动解析） */
        private String driverPath = "";
    }
}
