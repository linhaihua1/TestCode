package com.apiweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 拓扑配置（全部来自配置文件，禁止在代码中硬编码队列 / 交换机 / 路由键）。
 *
 * <p>对应 {@code application.yml} 的 {@code apiweb.mq.*} 节点：
 * <pre>
 * apiweb:
 *   mq:
 *     enabled: true
 *     exchange: apiweb.task.exchange        # 任务交换机（topic）
 *     result-exchange: apiweb.result.exchange
 *     queue:
 *       api: apiweb.task.api.queue
 *       ui: apiweb.task.ui.queue
 *       perf: apiweb.task.perf.queue
 *     routing-key:                          # 生产者投递时使用的路由键
 *       api: task.api.execute
 *       ui: task.ui.execute
 *       perf: task.perf.execute
 *     binding-key:                          # 队列绑定到交换机的通配路由键
 *       api: task.api.#
 *       ui: task.ui.#
 *       perf: task.perf.#
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "apiweb.mq")
public class MqProperties {

    /** 是否启用 RabbitMQ 消费端（localdev 单机调试时关闭，改用进程内 @Async 执行） */
    private boolean enabled = true;

    /** 任务交换机名称（topic） */
    private String exchange;

    /** 结果回传交换机名称（fanout） */
    private String resultExchange;

    /** 三类任务的队列名 */
    private TaskQueue queue = new TaskQueue();

    /** 生产者投递路由键 */
    private TaskKey routingKey = new TaskKey();

    /** 队列绑定路由键（支持通配符 #） */
    private TaskKey bindingKey = new TaskKey();

    /** 队列名集合 */
    @Data
    public static class TaskQueue {
        private String api;
        private String ui;
        private String perf;

        /** 按任务类型取队列名，未识别的类型返回 null */
        public String of(String taskType) {
            if (taskType == null) return null;
            return switch (taskType.toLowerCase()) {
                case "api" -> api;
                case "ui" -> ui;
                case "perf" -> perf;
                default -> null;
            };
        }
    }

    /** 路由键集合 */
    @Data
    public static class TaskKey {
        private String api;
        private String ui;
        private String perf;

        /** 按任务类型取路由键，未识别的类型返回 null */
        public String of(String taskType) {
            if (taskType == null) return null;
            return switch (taskType.toLowerCase()) {
                case "api" -> api;
                case "ui" -> ui;
                case "perf" -> perf;
                default -> null;
            };
        }
    }
}
