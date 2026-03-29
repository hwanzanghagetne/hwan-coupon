package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssue;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.dto.CouponIssueResponse;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 쓰기 작업을 담당하는 컴포넌트.
 * CouponService에서 Redis 호출 이후 DB 저장이 필요한 시점에 호출된다.
 *
 * 별도 클래스로 분리한 이유:
 * issueCoupon 흐름에서 Redis tryIssue(재고 차감)는 트랜잭션 밖에서 실행해야 한다.
 * 같은 클래스 내 메서드에 @Transactional을 붙이면 Spring AOP 프록시를 타지 못해
 * (self-invocation 문제) 트랜잭션이 적용되지 않는다.
 * 별도 빈으로 분리함으로써 Redis → DB 순서를 유지하면서 @Transactional을 정상 적용한다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueWriter {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponCacheService couponCacheService;

    @Transactional
    public Coupon saveCoupon(Coupon coupon) {
        return couponRepository.save(coupon);
    }

    @Transactional
    public CouponIssueResponse saveIssue(Long couponId, Long userId, long remaining) {
        CouponIssue couponIssue = CouponIssue.create(couponId, userId);
        couponIssueRepository.save(couponIssue);
        couponRepository.incrementIssuedQuantity(couponId);
        if (remaining == 0) {
            couponRepository.markExhausted(couponId, CouponStatus.EXHAUSTED);
            couponCacheService.evict(couponId);
        }
        return CouponIssueResponse.from(couponIssue);
    }
}
