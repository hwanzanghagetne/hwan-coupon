package com.hwan.coupon.global.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
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

    // 관리자 대량발급 컨슈머 전용 팩토리 — 기본 자동 ACK는 그대로 쓰되, 메시지 변환
    // 실패처럼 리스너 메서드 진입 전에 나는 예외도 재큐잉하지 않고 DLQ로 보내도록
    // defaultRequeueRejected만 false로 바꾼다. BatchProcessor 내부 처리 실패는
    // AmqpRejectAndDontRequeueException을 던져 같은 경로로 DLQ에 보존한다.
    @Bean
    public SimpleRabbitListenerContainerFactory adminBatchContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setContainerCustomizer(container -> container.setDefaultRequeueRejected(false));
        return factory;
    }

    // 선착순 발급 배치 컨슈머 전용 팩토리 — 짧은 주기로 모은 메시지를 하나의 DB 트랜잭션으로
    // 반영한 뒤에만 ack한다(AcknowledgeMode.MANUAL). 동시 소비자 1개는 JVM(인스턴스) 단위
    // 제한이라, 애플리케이션을 여러 대로 늘리면 소비자도 늘어난다 — 단일 인스턴스 배포 전제.
    @Bean
    public SimpleRabbitListenerContainerFactory firstComeBatchContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setConsumerBatchEnabled(true);
        factory.setBatchSize(500);
        factory.setBatchReceiveTimeout(200L);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}