package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.CouponIssueStatus;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.service.CouponCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponExpirySchedulerTest {

    @InjectMocks
    private CouponExpiryScheduler couponExpiryScheduler;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponIssueRepository couponIssueRepository;

    @Mock
    private CouponCacheService couponCacheService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("만료 대상 쿠폰이 없으면 DB 업데이트와 캐시 evict를 수행하지 않는다")
    void expireOverdueCoupons_만료대상없음_조기반환() {
        when(couponRepository.findExpiredActiveCouponIds(eq(CouponStatus.ACTIVE), any()))
                .thenReturn(List.of());

        couponExpiryScheduler.expireOverdueCoupons();

        verify(transactionTemplate, never()).execute(any());
        verify(couponCacheService, never()).evict(any());
    }

    @Test
    @DisplayName("만료 대상 쿠폰이 있으면 쿠폰과 발급이력을 벌크 업데이트하고 캐시를 evict한다")
    void expireOverdueCoupons_만료대상있음_업데이트_캐시evict() {
        List<Long> expiredIds = List.of(10L, 20L, 30L);
        when(couponRepository.findExpiredActiveCouponIds(eq(CouponStatus.ACTIVE), any()))
                .thenReturn(expiredIds);

        // TransactionTemplate이 콜백을 실제로 실행하도록 설정
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(couponRepository.markInactiveByIds(expiredIds, CouponStatus.INACTIVE, CouponStatus.ACTIVE))
                .thenReturn(3);
        when(couponIssueRepository.expireIssuedByCouponIds(expiredIds, CouponIssueStatus.EXPIRED, CouponIssueStatus.ISSUED))
                .thenReturn(15);

        couponExpiryScheduler.expireOverdueCoupons();

        // DB 업데이트 검증
        verify(couponRepository).markInactiveByIds(expiredIds, CouponStatus.INACTIVE, CouponStatus.ACTIVE);
        verify(couponIssueRepository).expireIssuedByCouponIds(expiredIds, CouponIssueStatus.EXPIRED, CouponIssueStatus.ISSUED);

        // 각 만료된 쿠폰 ID에 대해 캐시 evict 검증
        verify(couponCacheService).evict(10L);
        verify(couponCacheService).evict(20L);
        verify(couponCacheService).evict(30L);
    }

    @Test
    @DisplayName("DB 업데이트는 하나의 트랜잭션으로 묶여 원자적으로 실행된다")
    void expireOverdueCoupons_트랜잭션_단일처리() {
        List<Long> expiredIds = List.of(10L);
        when(couponRepository.findExpiredActiveCouponIds(eq(CouponStatus.ACTIVE), any()))
                .thenReturn(expiredIds);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(couponRepository.markInactiveByIds(any(), any(), any())).thenReturn(1);
        when(couponIssueRepository.expireIssuedByCouponIds(any(), any(), any())).thenReturn(1);

        couponExpiryScheduler.expireOverdueCoupons();

        // transactionTemplate.execute()가 정확히 1회만 호출됨 (쿠폰 + 발급이력 업데이트를 하나의 트랜잭션으로)
        verify(transactionTemplate, times(1)).execute(any());
    }
}
