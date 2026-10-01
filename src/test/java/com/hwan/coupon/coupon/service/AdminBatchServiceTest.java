package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.dto.BatchIssueResponse;
import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.infra.BatchMessagePayload;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBatchServiceTest {

    @InjectMocks
    private AdminBatchService adminBatchService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponIssueBatchRepository batchRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private Coupon adminIssuedCoupon() {
        return Coupon.create("관리자발급쿠폰", DiscountType.FIXED, 1000,
                null, null, IssueType.ADMIN_ISSUED, null, null,
                LocalDateTime.now().plusDays(30));
    }

    private Coupon firstComeCoupon() {
        return Coupon.create("선착순쿠폰", DiscountType.FIXED, 1000,
                100, null, IssueType.FIRST_COME, null, null,
                LocalDateTime.now().plusDays(30));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 ID로 배치 요청 시 COUPON_NOT_FOUND 예외가 발생한다")
    void requestBatch_쿠폰없음() {
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminBatchService.requestBatch(999L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("FIRST_COME 타입 쿠폰으로 배치 요청 시 COUPON_ISSUE_TYPE_MISMATCH 예외가 발생한다")
    void requestBatch_FIRST_COME_타입_불일치() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(firstComeCoupon()));

        assertThatThrownBy(() -> adminBatchService.requestBatch(1L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ISSUE_TYPE_MISMATCH);

        verify(batchRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("비활성화된 쿠폰으로 배치 요청 시 COUPON_NOT_ACTIVE 예외가 발생한다")
    void requestBatch_비활성쿠폰() {
        Coupon coupon = adminIssuedCoupon();
        ReflectionTestUtils.setField(coupon, "status", CouponStatus.INACTIVE);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> adminBatchService.requestBatch(1L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_ACTIVE);

        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 쿠폰으로 배치 요청 시 COUPON_EXPIRED 예외가 발생한다")
    void requestBatch_만료쿠폰() {
        Coupon coupon = adminIssuedCoupon();
        ReflectionTestUtils.setField(coupon, "expiredAt", LocalDateTime.now().minusDays(1));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> adminBatchService.requestBatch(1L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXPIRED);

        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("발행 중 연결 오류가 나면 예외를 던지지 않고 PENDING 상태와 batchId를 그대로 반환한다")
    void requestBatch_발행연결오류_PENDING_반환() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(adminIssuedCoupon()));
        CouponIssueBatch savedBatch = CouponIssueBatch.create(1L, 2);
        ReflectionTestUtils.setField(savedBatch, "id", 123L);
        when(batchRepository.save(any())).thenReturn(savedBatch);
        doThrow(new AmqpException("connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        BatchIssueResponse response = adminBatchService.requestBatch(1L, List.of(10L, 20L));

        assertThat(response.batchId()).isEqualTo(123L);
        assertThat(response.status()).isEqualTo(BatchStatus.PENDING);
    }

    @Test
    @DisplayName("중복 userId가 포함된 경우 deduplicate 후 targetCount에 고유 수만 반영한다")
    void requestBatch_중복userId_제거() {
        List<Long> userIdsWithDuplicate = List.of(1L, 2L, 3L, 2L, 1L); // 고유: 3명

        when(couponRepository.findById(1L)).thenReturn(Optional.of(adminIssuedCoupon()));
        CouponIssueBatch savedBatch = CouponIssueBatch.create(1L, 3);
        when(batchRepository.save(any())).thenReturn(savedBatch);

        adminBatchService.requestBatch(1L, userIdsWithDuplicate);

        // batchRepository.save()에 전달된 배치의 targetCount가 3인지 확인
        ArgumentCaptor<CouponIssueBatch> batchCaptor = ArgumentCaptor.forClass(CouponIssueBatch.class);
        verify(batchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getTargetCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("배치 요청 성공 시 RabbitMQ 메시지가 발행된다")
    void requestBatch_성공_메시지_발행() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(adminIssuedCoupon()));
        CouponIssueBatch savedBatch = CouponIssueBatch.create(1L, 2);
        when(batchRepository.save(any())).thenReturn(savedBatch);

        adminBatchService.requestBatch(1L, List.of(10L, 20L));

        // RabbitMQ 메시지 발행 검증
        ArgumentCaptor<BatchMessagePayload> payloadCaptor = ArgumentCaptor.forClass(BatchMessagePayload.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().userIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("존재하지 않는 batchId 조회 시 BATCH_NOT_FOUND 예외가 발생한다")
    void getBatchStatus_배치없음() {
        when(batchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminBatchService.getBatchStatus(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BATCH_NOT_FOUND);
    }
}
