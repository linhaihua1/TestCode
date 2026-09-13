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

    @RabbitListener(queues = "${apiweb.mq.queue.perf}")
    public void onMessage(EngineDtos.TaskMessage message) {
        log.info("收到性能测试任务: runId={}, caseId={}", message.getRunId(), message.getCaseId());
        perfExecutionService.executeAsync(message);
    }
}
