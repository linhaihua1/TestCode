package com.apiweb.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：任务异步投递拓扑。
 *
 * 拓扑设计：
 *   exchange: apiweb.task.exchange (topic)
 *     - routing key "task.api.#"  -> apiweb.task.api.queue   （接口自动化任务）
 *     - routing key "task.ui.#"   -> apiweb.task.ui.queue    （UI 自动化任务）
 *     - routing key "task.perf.#" -> apiweb.task.perf.queue  （性能测试任务）
 *   exchange: apiweb.result.exchange (fanout)  —— 执行结果回传（多消费者：报告落库、进度推送）
 */
@Configuration
public class RabbitMQConfig {

    public static final String TASK_EXCHANGE = "apiweb.task.exchange";
    public static final String RESULT_EXCHANGE = "apiweb.result.exchange";

    public static final String API_TASK_QUEUE = "apiweb.task.api.queue";
    public static final String UI_TASK_QUEUE = "apiweb.task.ui.queue";
    public static final String PERF_TASK_QUEUE = "apiweb.task.perf.queue";

    public static final String API_TASK_KEY = "task.api.execute";
    public static final String UI_TASK_KEY = "task.ui.execute";
    public static final String PERF_TASK_KEY = "task.perf.execute";

    // ---------- 交换机 ----------
    @Bean
    public TopicExchange taskExchange() {
        return ExchangeBuilder.topicExchange(TASK_EXCHANGE).durable(true).build();
    }

    @Bean
    public FanoutExchange resultExchange() {
        return ExchangeBuilder.fanoutExchange(RESULT_EXCHANGE).durable(true).build();
    }

    // ---------- 任务队列 ----------
    @Bean
    public Queue apiTaskQueue() {
        return QueueBuilder.durable(API_TASK_QUEUE).build();
    }

    @Bean
    public Queue uiTaskQueue() {
        return QueueBuilder.durable(UI_TASK_QUEUE).build();
    }

    @Bean
    public Queue perfTaskQueue() {
        return QueueBuilder.durable(PERF_TASK_QUEUE).build();
    }

    // ---------- 绑定 ----------
    @Bean
    public Binding apiTaskBinding() {
        return BindingBuilder.bind(apiTaskQueue()).to(taskExchange()).with("task.api.#");
    }

    @Bean
    public Binding uiTaskBinding() {
        return BindingBuilder.bind(uiTaskQueue()).to(taskExchange()).with("task.ui.#");
    }

    @Bean
    public Binding perfTaskBinding() {
        return BindingBuilder.bind(perfTaskQueue()).to(taskExchange()).with("task.perf.#");
    }

    // ---------- JSON 消息转换 ----------
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory factory, MessageConverter converter) {
        SimpleRabbitListenerContainerFactory listenerFactory = new SimpleRabbitListenerContainerFactory();
        listenerFactory.setConnectionFactory(factory);
        listenerFactory.setMessageConverter(converter);
        return listenerFactory;
    }
}
