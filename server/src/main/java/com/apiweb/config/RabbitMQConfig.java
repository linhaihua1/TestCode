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
 * <p>拓扑结构（交换机 / 队列 / 路由键名称全部来自 {@link MqProperties}，
 * 即 {@code application.yml} 的 {@code apiweb.mq.*}，代码中不再硬编码）：
 * <pre>
 *   exchange: {apiweb.mq.exchange} (topic)
 *     - routing key {apiweb.mq.binding-key.api}  -> {apiweb.mq.queue.api}   接口自动化任务
 *     - routing key {apiweb.mq.binding-key.ui}   -> {apiweb.mq.queue.ui}    UI 自动化任务
 *     - routing key {apiweb.mq.binding-key.perf} -> {apiweb.mq.queue.perf}  性能测试任务
 *   exchange: {apiweb.mq.result-exchange} (fanout)  执行结果回传
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    private final MqProperties mq;

    public RabbitMQConfig(MqProperties mq) {
        this.mq = mq;
    }

    // ---------- 交换机 ----------
    @Bean
    public TopicExchange taskExchange() {
        return ExchangeBuilder.topicExchange(mq.getExchange()).durable(true).build();
    }

    @Bean
    public FanoutExchange resultExchange() {
        return ExchangeBuilder.fanoutExchange(mq.getResultExchange()).durable(true).build();
    }

    // ---------- 任务队列 ----------
    @Bean
    public Queue apiTaskQueue() {
        return QueueBuilder.durable(mq.getQueue().getApi()).build();
    }

    @Bean
    public Queue uiTaskQueue() {
        return QueueBuilder.durable(mq.getQueue().getUi()).build();
    }

    @Bean
    public Queue perfTaskQueue() {
        return QueueBuilder.durable(mq.getQueue().getPerf()).build();
    }

    // ---------- 绑定 ----------
    @Bean
    public Binding apiTaskBinding() {
        return BindingBuilder.bind(apiTaskQueue()).to(taskExchange())
                .with(mq.getBindingKey().getApi());
    }

    @Bean
    public Binding uiTaskBinding() {
        return BindingBuilder.bind(uiTaskQueue()).to(taskExchange())
                .with(mq.getBindingKey().getUi());
    }

    @Bean
    public Binding perfTaskBinding() {
        return BindingBuilder.bind(perfTaskQueue()).to(taskExchange())
                .with(mq.getBindingKey().getPerf());
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
