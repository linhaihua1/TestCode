package com.apiweb.mq;

import com.apiweb.config.MqProperties;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务生产者：把执行任务异步投递到 RabbitMQ。
 *
 * <p>交换机 / 路由键均取自 {@link MqProperties}（配置文件），按任务类型路由：
 * {@code routingKey.api} / {@code routingKey.ui} / {@code routingKey.perf}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MqProperties mq;

    public void sendApiTask(String runId, String projectId, String taskId,
                            String caseId, String environmentId) {
        send(new EngineDtos.TaskMessage(TaskType.API.getCode(), runId, projectId,
                caseId, taskId, environmentId, null));
        log.info("API 任务已投递: runId={}, caseId={}", runId, caseId);
    }

    public void sendUiTask(String runId, String projectId, String caseId) {
        sendUiTask(runId, projectId, caseId, null, null);
    }

    /**
     * 投递 UI 任务，携带执行环境与执行参数（重试/超时/执行机等）。
     */
    public void sendUiTask(String runId, String projectId, String caseId,
                           String environmentId, Map<String, Object> extra) {
        send(new EngineDtos.TaskMessage(TaskType.UI.getCode(), runId, projectId,
                caseId, null, environmentId, extra));
        log.info("UI 任务已投递: runId={}, caseId={}, environmentId={}", runId, caseId, environmentId);
    }

    public void sendPerfTask(String runId, String projectId, String caseId) {
        send(new EngineDtos.TaskMessage(TaskType.PERF.getCode(), runId, projectId,
                caseId, null, null, null));
        log.info("性能测试任务已投递: runId={}, caseId={}", runId, caseId);
    }

    /**
     * 统一投递：按 {@code taskType} 从配置取路由键后发送到任务交换机。
     */
    private void send(EngineDtos.TaskMessage message) {
        TaskType type = TaskType.parse(message.getTaskType());
        if (type == null) {
            throw new IllegalArgumentException("未知任务类型: " + message.getTaskType());
        }
        if (!mq.isEnabled()) {
            // localdev 等关闭 MQ 的场景：不投递，由进程内 @Async 直接执行
            log.debug("MQ 已禁用，跳过投递: type={}, runId={}", type.getCode(), message.getRunId());
            return;
        }
        rabbitTemplate.convertAndSend(mq.getExchange(),
                mq.getRoutingKey().of(type.getCode()), message);
    }
}
