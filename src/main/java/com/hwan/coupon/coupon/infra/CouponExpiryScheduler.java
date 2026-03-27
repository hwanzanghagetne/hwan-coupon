package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.CouponIssueStatus;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.service.CouponCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpiryScheduler {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponCacheService couponCacheService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "0 0 2 * * *")
    public void expireOverdueCoupons() {
        LocalDateTime now = LocalDateTime.now();

        // 1단계: 만료 대상 쿠폰 ID 조회 (트랜잭션 없이 단순 SELECT)
        List<Long> expiredCouponIds = couponRepository.findExpiredActiveCouponIds(CouponStatus.ACTIVE, now);

        if (expiredCouponIds.isEmpty()) {
            log.info("[CouponExpiryScheduler] 만료 대상 없음");
            return;
        }

        // 2단계: 쿠폰 상태 변경 + 발급이력 상태 변경을 하나의 트랜잭션으로 묶음
        // 둘 중 하나라도 실패하면 둘 다 롤백 (원자성 보장)
        // @Transactional 대신 TransactionTemplate을 쓰는 이유:
        //   이 블록이 끝나면 즉시 커밋됨 → 이후 Redis evict를 DB 커밋 완료 후 실행 가능
        int[] result = transactionTemplate.execute(status -> {
            int coupons = couponRepository.markInactiveByIds(expiredCouponIds, CouponStatus.INACTIVE, CouponStatus.ACTIVE);
            int issues = couponIssueRepository.expireIssuedByCouponIds(expiredCouponIds, CouponIssueStatus.EXPIRED, CouponIssueStatus.ISSUED);
            return new int[]{coupons, issues};
        });

        log.info("[CouponExpiryScheduler] 완료 — 쿠폰 {}건, 발급이력 {}건 처리", result[0], result[1]);

        // 3단계: DB 커밋 완료 후 Redis 캐시 삭제
        // 캐시엔 status=ACTIVE가 남아있으므로 evict 하지 않으면 발급 요청 시 캐시 기준으로 ACTIVE 판단
        expiredCouponIds.forEach(couponCacheService::evict);
    }
}
