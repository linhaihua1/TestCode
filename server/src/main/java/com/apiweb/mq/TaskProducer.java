package com.apiweb.mq;

import com.apiweb.config.RabbitMQConfig;
import com.apiweb.engine.EngineDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 任务生产者：把执行任务异步投递到 RabbitMQ。
 * 按任务类型路由：task.api.* / task.ui.* / task.perf.*
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendApiTask(String runId, String projectId, String taskId,
                            String caseId, String environmentId) {
        EngineDtos.TaskMessage message = new EngineDtos.TaskMessage(
                "api", runId, projectId, caseId, taskId, environmentId, null);
        rabbitTemplate.convertAndSend(RabbitMQConfig.TASK_EXCHANGE,
                RabbitMQConfig.API_TASK_KEY, message);
        log.info("API 任务已投递: runId={}, caseId={}", runId, caseId);
    }

    public void sendUiTask(String runId, String projectId, String caseId) {
        EngineDtos.TaskMessage message = new EngineDtos.TaskMessage(
                "ui", runId, projectId, caseId, null, null, null);
        rabbitTemplate.convertAndSend(RabbitMQConfig.TASK_EXCHANGE,
                RabbitMQConfig.UI_TASK_KEY, message);
        log.info("UI 任务已投递: runId={}, caseId={}", runId, caseId);
    }

    public void sendPerfTask(String runId, String projectId, String caseId) {
        EngineDtos.TaskMessage message = new EngineDtos.TaskMessage(
                "perf", runId, projectId, caseId, null, null, null);
        rabbitTemplate.convertAndSend(RabbitMQConfig.TASK_EXCHANGE,
                RabbitMQConfig.PERF_TASK_KEY, message);
        log.info("性能测试任务已投递: runId={}, caseId={}", runId, caseId);
    }
}
