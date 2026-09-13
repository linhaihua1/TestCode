package com.apiweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CI/CD 流水线相关配置（前缀 {@code apiweb.ci}）。
 *
 * <h3>为什么外置</h3>
 * Jenkins 地址、超时时间、轮询间隔、触发频率限制等在不同环境差异很大，
 * 且属于运维调优参数，硬编码会导致每次调整都要改代码发版。统一由
 * {@code application.yml} 承载并支持环境变量覆盖（如 {@code CI_JENKINS_CONNECT_TIMEOUT_MS}）。
 *
 * <h3>主要分组</h3>
 * <ul>
 *   <li>{@code publicBaseUrl}：生成 Jenkinsfile / curl 示例时使用的平台外网地址</li>
 *   <li>{@code triggerMinIntervalSeconds}：同一流水线的最小触发间隔，防止 CI 误重复触发</li>
 *   <li>{@code jenkins}：Jenkins Remote API 的超时与轮询参数</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "apiweb.ci")
public class CiProperties {

    /** 平台对外访问地址（生成 Jenkinsfile / 触发命令示例时使用），结尾不带 / */
    private String publicBaseUrl = "http://localhost:8080";

    /** 同一流水线的最小触发间隔（秒），0 表示不限制 */
    private int triggerMinIntervalSeconds = 5;

    /** 执行记录列表返回条数上限 */
    private int listMaxLimit = 100;

    /** Jenkins Remote API 调用参数 */
    private Jenkins jenkins = new Jenkins();

    /**
     * Jenkins 调用相关参数。
     */
    @Data
    public static class Jenkins {

        /** 建立 HTTP 连接超时（毫秒） */
        private int connectTimeoutMs = 5000;

        /** 读取响应超时（毫秒） */
        private int readTimeoutMs = 15000;

        /** 触发后解析队列项 → 构建号的轮询间隔（毫秒） */
        private int queuePollIntervalMs = 2000;

        /** 解析构建号的最大等待时间（毫秒），超时记为执行异常 */
        private int queuePollTimeoutMs = 60000;

        /** 轮询构建状态的间隔（毫秒） */
        private int buildPollIntervalMs = 5000;

        /** 轮询构建状态的最大时长（分钟），超时记为执行异常 */
        private int buildPollTimeoutMinutes = 120;

        /** 回写的 Jenkins 控制台日志最大字符数（超出截断，避免撑爆数据库） */
        private int consoleMaxChars = 20000;
    }
}
