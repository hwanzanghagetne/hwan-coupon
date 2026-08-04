package com.hwan.coupon.global.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE    = "coupon.exchange";
    public static final String QUEUE       = "coupon.batch.issue";
    public static final String ROUTING_KEY = "coupon.batch.issue";

    public static final String DLX             = "coupon.dlx";
    public static final String DLQ             = "coupon.batch.issue.dlq";
    public static final String DLQ_ROUTING_KEY = "coupon.batch.issue.dlq";

    // 선착순 발급 — 당첨 확정 후 DB 반영을 큐로 순차화해 동시 쓰기 경합(데드락)을 구조적으로 제거
    public static final String QUEUE_FIRST_COME           = "coupon.firstcome.issue";
    public static final String ROUTING_KEY_FIRST_COME     = "coupon.firstcome.issue";
    public static final String DLQ_FIRST_COME             = "coupon.firstcome.issue.dlq";
    public static final String DLQ_ROUTING_KEY_FIRST_COME = "coupon.firstcome.issue.dlq";

    @Bean
    public DirectExchange couponExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue couponBatchQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding couponBatchBinding() {
        return BindingBuilder.bind(couponBatchQueue())
                .to(couponExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    public Queue couponFirstComeQueue() {
        return QueueBuilder.durable(QUEUE_FIRST_COME)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY_FIRST_COME)
                .build();
    }

    @Bean
    public Binding couponFirstComeBinding() {
        return BindingBuilder.bind(couponFirstComeQueue())
                .to(couponExchange())
                .with(ROUTING_KEY_FIRST_COME);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue firstComeDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_FIRST_COME).build();
    }

    @Bean
    public Binding firstComeDeadLetterBinding() {
        return BindingBuilder.bind(firstComeDeadLetterQueue())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY_FIRST_COME);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}