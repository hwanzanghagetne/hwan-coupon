package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.service.CouponRedisService;
import com.hwan.coupon.global.config.RabbitMQConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstComeIssueReconciliationSchedulerTest {

    @InjectMocks
    private FirstComeIssueReconciliationScheduler scheduler;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponIssueRepository couponIssueRepository;

    @Mock
    private CouponRedisService couponRedisService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private Coupon coupon(Long id) {
        Coupon coupon = Coupon.create("테스트", DiscountType.FIXED, 1000, 10, null,
                IssueType.FIRST_COME, null, null, LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(coupon, "id", id);
        return coupon;
    }

    @Test
    @DisplayName("Redis에는 있고 DB에는 없는 당첨자만 재발행한다")
    void reconcile_DB미반영_당첨자만_재발행() {
        when(couponRepository.findByIssueTypeAndStatusIn(eq(IssueType.FIRST_COME), anyList()))
                .thenReturn(List.of(coupon(1L)));
        when(couponRedisService.getIssuedUserIds(1L)).thenReturn(Set.of("10", "20"));
        when(couponIssueRepository.findUserIdsByCouponId(1L)).thenReturn(List.of(10L));

        scheduler.reconcile();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY_FIRST_COME),
                eq(new FirstComeIssuePayload(1L, 20L)));
        verify(rabbitTemplate, never()).convertAndSend(
                anyString(), anyString(), eq(new FirstComeIssuePayload(1L, 10L)));
    }

    @Test
    @DisplayName("Redis 당첨자가 이미 전부 DB에 반영돼 있으면 아무 것도 재발행하지 않는다")
    void reconcile_이미_전부_반영되면_재발행없음() {
        when(couponRepository.findByIssueTypeAndStatusIn(eq(IssueType.FIRST_COME), anyList()))
                .thenReturn(List.of(coupon(1L)));
        when(couponRedisService.getIssuedUserIds(1L)).thenReturn(Set.of("10"));
        when(couponIssueRepository.findUserIdsByCouponId(1L)).thenReturn(List.of(10L));

        scheduler.reconcile();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Redis 당첨자가 없는 쿠폰은 DB 조회 자체를 하지 않는다")
    void reconcile_Redis당첨자없으면_DB조회안함() {
        when(couponRepository.findByIssueTypeAndStatusIn(eq(IssueType.FIRST_COME), anyList()))
                .thenReturn(List.of(coupon(1L)));
        when(couponRedisService.getIssuedUserIds(1L)).thenReturn(Set.of());

        scheduler.reconcile();

        verifyNoInteractions(couponIssueRepository, rabbitTemplate);
    }

    @Test
    @DisplayName("한 쿠폰 대사 중 예외가 나도 다음 쿠폰은 계속 처리한다")
    void reconcile_한쿠폰_실패해도_다음쿠폰_계속처리() {
        when(couponRepository.findByIssueTypeAndStatusIn(eq(IssueType.FIRST_COME), anyList()))
                .thenReturn(List.of(coupon(1L), coupon(2L)));
        when(couponRedisService.getIssuedUserIds(1L)).thenThrow(new RuntimeException("Redis 조회 실패"));
        when(couponRedisService.getIssuedUserIds(2L)).thenReturn(Set.of("30"));
        when(couponIssueRepository.findUserIdsByCouponId(2L)).thenReturn(List.of());

        scheduler.reconcile();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY_FIRST_COME),
                eq(new FirstComeIssuePayload(2L, 30L)));
    }
}
