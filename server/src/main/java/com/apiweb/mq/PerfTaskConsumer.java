package com.apiweb.mq;

import com.apiweb.engine.EngineDtos;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 性能测试任务消费者：调用内嵌 JMeter 引擎执行压测，结果回写 t_perf_report。
 * 消费的队列名来自配置 {@code apiweb.mq.queue.perf}，不硬编码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "apiweb.mq.enabled", havingValue = "true", matchIfMissing = true)
public class PerfTaskConsumer {

    private final com.apiweb.service.PerfExecutionService perfExecutionService;

    /**
     * 消费性能测试任务消息。
     *
     * <p>说明：
     * <ul>
     *   <li>本监听器使用 RabbitMQ 默认 <b>自动 ack</b>（消息进入方法即视为已确认），
     *       不启用手动 ack：性能压测由 {@link PerfExecutionService#executeAsync} 异步执行，
     *       方法内不做阻塞等待，故消息可立即确认，避免执行期间一直占用预取计数；</li>
     *   <li>若需「执行成功才算消费」的严格语义，可改为 {@code ackMode=MANUAL} 并在
     *       {@code executeAsync} 完成后手动 {@code basicAck}，但需额外处理重试与死信；</li>
     *   <li>实际压测逻辑在 {@code executeAsync} 中启动内嵌 JMeter 引擎，结果回写
     *       {@code t_perf_report}。</li>
     * </ul>
     *
     * @param message MQ 任务消息
     */
    @RabbitListener(queues = "${apiweb.mq.queue.perf}")
    public void onMessage(EngineDtos.TaskMessage message) {
        log.info("收到性能测试任务: runId={}, caseId={}", message.getRunId(), message.getCaseId());
        perfExecutionService.executeAsync(message);
    }
}
