package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.service.CouponIssueWriter;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstComeIssueProcessorTest {

    @InjectMocks
    private FirstComeIssueProcessor processor;

    @Mock
    private CouponIssueWriter couponIssueWriter;

    @Mock
    private MessageConverter messageConverter;

    @Mock
    private Channel channel;

    private Message message(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }

    @Test
    @DisplayName("DB 반영에 성공하면 마지막 delivery tag까지 ack한다")
    void processBatch_성공시_ack() throws Exception {
        Message msg = message(1L);
        when(messageConverter.fromMessage(msg)).thenReturn(new FirstComeIssuePayload(1L, 10L));
        when(couponIssueWriter.saveIssueBatch(any())).thenReturn(1);

        processor.processBatch(List.of(msg), channel);

        verify(channel).basicAck(1L, true);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("메시지 변환에 실패하면 DLQ로 보내고 재큐잉하지 않는다")
    void processBatch_변환실패시_DLQ로() throws Exception {
        Message msg = message(2L);
        when(messageConverter.fromMessage(msg)).thenThrow(new IllegalArgumentException("잘못된 메시지"));

        processor.processBatch(List.of(msg), channel);

        verify(channel).basicNack(2L, true, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("DB 반영에 실패하면 DLQ로 보내고 재큐잉하지 않는다")
    void processBatch_DB반영실패시_DLQ로() throws Exception {
        Message msg = message(3L);
        when(messageConverter.fromMessage(msg)).thenReturn(new FirstComeIssuePayload(1L, 10L));
        when(couponIssueWriter.saveIssueBatch(any())).thenThrow(new RuntimeException("DB 오류"));

        processor.processBatch(List.of(msg), channel);

        verify(channel).basicNack(3L, true, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("메시지가 비어있으면 아무 것도 하지 않는다")
    void processBatch_빈메시지는_무시() {
        assertThatCode(() -> processor.processBatch(List.of(), channel)).doesNotThrowAnyException();

        verifyNoInteractions(couponIssueWriter, messageConverter);
    }
}
