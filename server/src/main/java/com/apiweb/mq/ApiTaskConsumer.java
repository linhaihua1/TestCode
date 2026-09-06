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
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务消费者：消费接口自动化任务，执行用例并落 TestTaskRun。
 * 手动 ack：执行成功 ack；失败也 ack（结果已记录在 run 表，避免无限重投）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiTaskConsumer {

    private final TestTaskMapper testTaskMapper;
    private final TestTaskRunMapper testTaskRunMapper;
    private final CaseMapper caseMapper;
    private final ExecutionSupportService executionSupport;

    @RabbitListener(queues = RabbitMQConfig.API_TASK_QUEUE)
    public void onMessage(@Payload EngineDtos.TaskMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        try {
            process(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("API 任务消费失败: runId={}", message.getRunId(), e);
            markError(message.getRunId(), e.getMessage());
            channel.basicAck(deliveryTag, false);
        }
    }

    private void process(EngineDtos.TaskMessage message) {
        String runId = message.getRunId();
        TestTaskRunEntity run = testTaskRunMapper.selectById(runId);
        if (run == null) {
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

        // 组装变量：任务变量 + 环境变量 + 全局变量
        Map<String, String> vars = new LinkedHashMap<>();
        if (task != null) {
            vars.putAll(executionSupport.kvToMap(task.getVariables()));
        }
        vars.putAll(executionSupport.buildEnvironmentVariables(
                task != null ? task.getEnvironmentId() : message.getEnvironmentId()));
        vars.putAll(executionSupport.buildGlobalVariables(caseEntity.getProjectId()));

        EngineDtos.ExecutionResult result = executionSupport.caseRunner()
                .run(caseEntity.getSteps(), new VariablesResolver(vars));
        if (task != null && task.getBaseUrl() != null && !task.getBaseUrl().isBlank()) {
            vars.put("baseUrl", task.getBaseUrl());
        }

        // 失败重试
        int retry = task == null ? 0 : task.getRetryCount();
        int attempts = 0;
        while (!"success".equals(result.getResult()) && attempts < retry) {
            attempts++;
            result = executionSupport.caseRunner()
                    .run(caseEntity.getSteps(), new VariablesResolver(new HashMap<>(vars)));
        }

        run.setResult(result.getResult());
        run.setDuration((int) (System.currentTimeMillis() - start));
        run.setEndedAt(Instant.now());
        run.setDetails(executionSupport.offloadDetails(runId, JsonUtils.toJson(result)));
        testTaskRunMapper.updateById(run);

        // 任务回调通知
        if (task != null && task.getNotifyUrl() != null && !task.getNotifyUrl().isBlank()) {
            executionSupport.notifyCallback(task.getNotifyUrl(), JsonUtils.toJson(Map.of(
                    "runId", runId, "taskId", task.getId(),
                    "result", result.getResult(), "duration", run.getDuration())));
        }
    }

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
