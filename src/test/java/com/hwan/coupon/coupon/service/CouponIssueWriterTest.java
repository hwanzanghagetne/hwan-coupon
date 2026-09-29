package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.infra.CouponIssueBulkInsertSupport;
import com.hwan.coupon.coupon.infra.FirstComeIssuePayload;
import com.hwan.coupon.coupon.repository.CouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponIssueWriterTest {

    @InjectMocks
    private CouponIssueWriter couponIssueWriter;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponIssueBulkInsertSupport bulkInsertSupport;

    @Mock
    private CouponCacheService couponCacheService;

    @Test
    @DisplayName("같은 발급 메시지가 재전달돼 실제 INSERT가 0건이면 수량 반영은 건너뛰지만 캐시는 evict한다")
    void saveIssueBatch_중복재전달은_수량증가없음() {
        List<FirstComeIssuePayload> payloads = List.of(new FirstComeIssuePayload(1L, 10L));
        when(bulkInsertSupport.insertIgnore(eq(1L), anyList())).thenReturn(0);

        int inserted = couponIssueWriter.saveIssueBatch(payloads);

        assertThat(inserted).isZero();
        verify(couponRepository, never()).incrementIssuedQuantityByAndMarkExhausted(any(), anyInt(), any(), any());
        verify(couponCacheService).evict(1L);
    }

    @Test
    @DisplayName("실제로 삽입된 건수만큼만 issuedQuantity를 반영하고 캐시를 evict한다")
    void saveIssueBatch_실제삽입건수만_반영() {
        List<FirstComeIssuePayload> payloads = List.of(new FirstComeIssuePayload(1L, 10L));
        when(bulkInsertSupport.insertIgnore(eq(1L), anyList())).thenReturn(1);

        int inserted = couponIssueWriter.saveIssueBatch(payloads);

        assertThat(inserted).isEqualTo(1);
        verify(couponRepository).incrementIssuedQuantityByAndMarkExhausted(eq(1L), eq(1), eq(CouponStatus.EXHAUSTED), any(LocalDateTime.class));
        verify(couponCacheService).evict(1L);
    }

    @Test
    @DisplayName("여러 쿠폰의 당첨자가 한 배치에 섞여도 쿠폰별 실제 삽입 건수만 각각 반영된다")
    void saveIssueBatch_여러쿠폰_섞여도_쿠폰별로_반영() {
        List<FirstComeIssuePayload> payloads = List.of(
                new FirstComeIssuePayload(1L, 10L),
                new FirstComeIssuePayload(1L, 11L),
                new FirstComeIssuePayload(2L, 20L)
        );
        when(bulkInsertSupport.insertIgnore(eq(1L), anyList())).thenReturn(2);
        when(bulkInsertSupport.insertIgnore(eq(2L), anyList())).thenReturn(0);

        int inserted = couponIssueWriter.saveIssueBatch(payloads);

        assertThat(inserted).isEqualTo(2);
        verify(couponRepository).incrementIssuedQuantityByAndMarkExhausted(eq(1L), eq(2), eq(CouponStatus.EXHAUSTED), any(LocalDateTime.class));
        verify(couponRepository, never()).incrementIssuedQuantityByAndMarkExhausted(eq(2L), anyInt(), any(), any());
        verify(couponCacheService).evict(1L);
        verify(couponCacheService).evict(2L);
    }

    @Test
    @DisplayName("빈 배치는 아무 작업도 하지 않는다")
    void saveIssueBatch_빈배치는_무시() {
        int inserted = couponIssueWriter.saveIssueBatch(List.of());

        assertThat(inserted).isZero();
        verifyNoInteractions(bulkInsertSupport, couponRepository, couponCacheService);
    }
}
