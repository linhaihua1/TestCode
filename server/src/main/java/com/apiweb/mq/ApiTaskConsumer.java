package com.apiweb.mq;

import com.apiweb.config.RabbitMQConfig;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.VariablesResolver;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.entity.TestTaskRunEntity;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.TestTaskMapper;
import com.apiweb.mapper.TestTaskRunMapper;
import com.apiweb.service.ExecutionSupportService;
import com.apiweb.util.JsonUtils;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接口自动化任务消费者。
 *
 * <p>消费 RabbitMQ 队列 {@code apiweb.task.api.queue} 中的任务消息，调用 {@code CaseRunner} 执行用例，
 * 结果写入 {@code t_test_task_run}。
 *
 * <h3>消息流</h3>
 * <ol>
 *   <li>Controller 调用 {@link TaskProducer} 投递 {@link EngineDtos.TaskMessage}</li>
 *   <li>本消费者从队列取出，更新 run 状态为 "running"</li>
 *   <li>组装变量（任务级 + 环境级 + 全局级），执行用例</li>
 *   <li>支持任务级重试（{@code retryCount}）</li>
 *   <li>把结果写到 run 详情（含详细执行步骤）</li>
 *   <li>如有回调 URL，POST 执行结果给调用方</li>
 *   <li>无论成败都 ack（结果已落库，避免无限重投）</li>
 * </ol>
 *
 * <h3>为什么用手动 ack？</h3>
 * <ul>
 *   <li>执行结果已持久化到 MySQL，重投没有意义（会重复写 run 详情）</li>
 *   <li>失败信息记录到 run.details.message，前端可显示</li>
 *   <li>如果需要重试，应由用户在 UI 上重新触发，而非 RabbitMQ 自动重投</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "apiweb.mq.enabled", havingValue = "true", matchIfMissing = true)
public class ApiTaskConsumer {

    private final TestTaskMapper testTaskMapper;
    private final TestTaskRunMapper testTaskRunMapper;
    private final CaseMapper caseMapper;
    private final ExecutionSupportService executionSupport;

    /**
     * RabbitMQ 消息处理入口。
     *
     * @param message     反序列化后的任务消息
     * @param channel     RabbitMQ Channel（用于手动 ack）
     * @param deliveryTag 消息投递标签
     */
    @RabbitListener(queues = RabbitMQConfig.API_TASK_QUEUE)
    public void onMessage(@Payload EngineDtos.TaskMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        try {
            process(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 业务异常也 ack（结果已落库），避免 RabbitMQ 无限重投
            log.error("API 任务消费失败: runId={}", message.getRunId(), e);
            markError(message.getRunId(), e.getMessage());
            channel.basicAck(deliveryTag, false);
        }
    }

    /**
     * 业务逻辑：执行用例 + 写结果。
     */
    private void process(EngineDtos.TaskMessage message) {
        String runId = message.getRunId();
        TestTaskRunEntity run = testTaskRunMapper.selectById(runId);
        if (run == null) {
            // runId 可能已被清理，跳过即可
            log.warn("Run 不存在，忽略: {}", runId);
            return;
        }
        run.setResult("running");
        testTaskRunMapper.updateById(run);

        long start = System.currentTimeMillis();
        TestTaskEntity task = testTaskMapper.selectById(message.getTaskId());
        CaseEntity caseEntity = caseMapper.selectById(message.getCaseId());
        if (caseEntity == null) {
            markError(runId, "用例不存在: " + message.getCaseId());
            return;
        }

        // 变量按优先级组装：任务级 > 环境级 > 全局级
        Map<String, String> vars = new LinkedHashMap<>();
        if (task != null) {
            vars.putAll(executionSupport.kvToMap(task.getVariables()));
        }
        vars.putAll(executionSupport.buildEnvironmentVariables(
                task != null ? task.getEnvironmentId() : message.getEnvironmentId()));
        vars.putAll(executionSupport.buildGlobalVariables(caseEntity.getProjectId()));

        EngineDtos.ExecutionResult result = executionSupport.caseRunner()
                .run(caseEntity.getSteps(), new VariablesResolver(vars));
        // 任务级 baseUrl 覆盖环境变量（优先级最高）
        if (task != null && task.getBaseUrl() != null && !task.getBaseUrl().isBlank()) {
            vars.put("baseUrl", task.getBaseUrl());
        }

        // 失败重试（仅当 result != success 且 retryCount > 0 时触发）
        // 注意：重试用全新变量池，避免上一次的提取变量污染
        int retry = task == null ? 0 : task.getRetryCount();
        int attempts = 0;
        while (!"success".equals(result.getResult()) && attempts < retry) {
            attempts++;
            result = executionSupport.caseRunner()
                    .run(caseEntity.getSteps(), new VariablesResolver(new HashMap<>(vars)));
        }

        // 写回结果
        run.setResult(result.getResult());
        run.setDuration((int) (System.currentTimeMillis() - start));
        run.setEndedAt(Instant.now());
        run.setDetails(executionSupport.offloadDetails(runId, JsonUtils.toJson(result)));
        testTaskRunMapper.updateById(run);

        // 异步通知（不阻塞消费流程）
        if (task != null && task.getNotifyUrl() != null && !task.getNotifyUrl().isBlank()) {
            executionSupport.notifyCallback(task.getNotifyUrl(), JsonUtils.toJson(Map.of(
                    "runId", runId, "taskId", task.getId(),
                    "result", result.getResult(), "duration", run.getDuration())));
        }
    }

    /**
     * 异常情况兜底：把 run 标记为 error 并记录原因。
     */
    private void markError(String runId, String message) {
        TestTaskRunEntity run = testTaskRunMapper.selectById(runId);
        if (run != null) {
            run.setResult("error");
            run.setEndedAt(Instant.now());
            Map<String, Object> err = new HashMap<>();
            err.put("error", message);
            run.setDetails(JsonUtils.toJson(err));
            testTaskRunMapper.updateById(run);
        }
    }
}
