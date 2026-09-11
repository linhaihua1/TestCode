package com.apiweb.mq;

import com.apiweb.engine.EngineDtos;
import com.apiweb.service.UiExecutionService;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * UI 自动化任务消费者。
 * 说明：UI 自动化需要执行机侧的 Selenium WebDriver。本消费者负责接收任务并分派：
 *  - 若本机是执行机（apiweb.executor.local-ui=true），本机驱动 Chrome 执行；
 *  - 否则任务保留在队列，由注册到资源池的 UI 执行机消费。
 * 执行结果回写 t_ui_report。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "apiweb.mq.enabled", havingValue = "true", matchIfMissing = true)
public class UiTaskConsumer {

    private final UiExecutionService uiExecutionService;

    @RabbitListener(queues = "apiweb.task.ui.queue")
    public void onMessage(EngineDtos.TaskMessage message) {
        log.info("收到 UI 任务: runId={}, caseId={}", message.getRunId(), message.getCaseId());
        uiExecutionService.executeAsync(message);
    }
}
