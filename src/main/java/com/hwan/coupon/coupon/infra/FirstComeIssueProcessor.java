package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.service.CouponIssueWriter;
import com.hwan.coupon.global.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 선착순 발급 당첨자를 짧은 주기(최대 200ms 또는 500건)로 모아 한 번에 DB에 반영하는 컨슈머.
 *
 * 메시지를 받는 즉시 ack하지 않는다({@link org.springframework.amqp.core.AcknowledgeMode#MANUAL}).
 * {@link CouponIssueWriter#saveIssueBatch}가 DB 트랜잭션을 커밋한 뒤에만 배치 전체를 ack하므로,
 * 커밋 전에 컨슈머가 죽어도 메시지는 재전달된다(재전달돼도 coupon_issue의
 * UNIQUE(coupon_id, user_id) + INSERT IGNORE로 중복 삽입은 안전하게 무시된다).
 *
 * 동시 소비자를 1개로 고정({@link RabbitMQConfig#firstComeBatchContainerFactory})해
 * DB에 동시에 쓰는 주체가 여러 개가 되는 상황을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeIssueProcessor {

    private final CouponIssueWriter couponIssueWriter;
    private final MessageConverter messageConverter;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_FIRST_COME, containerFactory = "firstComeBatchContainerFactory")
    public void processBatch(List<Message> messages, Channel channel) throws IOException {
        if (messages.isEmpty()) {
            return;
        }

        long lastDeliveryTag = messages.get(messages.size() - 1).getMessageProperties().getDeliveryTag();

        try {
            List<FirstComeIssuePayload> payloads = messages.stream()
                    .map(m -> (FirstComeIssuePayload) messageConverter.fromMessage(m))
                    .toList();

            int inserted = couponIssueWriter.saveIssueBatch(payloads);
            log.info("선착순 배치 반영 완료 messageCount={} insertedCount={}", payloads.size(), inserted);

            channel.basicAck(lastDeliveryTag, true);
        } catch (Exception e) {
            log.error("선착순 배치 반영 실패, DLQ로 이동 messageCount={} error={}", messages.size(), e.getMessage(), e);
            channel.basicNack(lastDeliveryTag, true, false);
        }
    }
}
