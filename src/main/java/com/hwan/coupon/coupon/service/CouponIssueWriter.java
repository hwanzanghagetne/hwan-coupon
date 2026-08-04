package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.CouponIssue;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.dto.CouponIssueResponse;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponIssueRequestRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 이력 DB 쓰기를 담당하는 컴포넌트.
 *
 * 별도 클래스로 분리한 이유:
 * issueCoupon 흐름에서 Redis tryIssue(재고 차감)는 트랜잭션 밖에서 실행해야 한다.
 * 같은 클래스 내 메서드에 @Transactional을 붙이면 Spring AOP 프록시를 타지 못해
 * (self-invocation 문제) 트랜잭션이 적용되지 않는다.
 * 별도 빈으로 분리함으로써 Redis → DB 순서를 유지하면서 @Transactional을 정상 적용한다.
 *
 * @Retryable을 이 DB 쓰기 구간에만 좁혀서 적용한 이유:
 * issueCoupon() 전체를 재시도하면 재시도 시작 전에 이미 CouponService의 catch 블록이
 * Redis 재고를 롤백해버려서, 재시도가 tryIssue()부터 다시 경쟁하게 된다 — 그 사이 다른
 * 요청이 자리를 가져가면 원래 당첨자가 오히려 탈락할 수 있다. saveIssue()만 재시도하면
 * Redis 당첨(재고 확보) 상태를 유지한 채로 DB 쓰기만 다시 시도하므로, 데드락이 풀릴 때까지
 * 자리를 지킨 채 안전하게 재시도할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueWriter {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponIssueRequestRepository issueRequestRepository;
    private final CouponCacheService couponCacheService;

    /**
     * 선착순 발급 요청 하나를 실제 DB에 반영한다. FirstComeIssueProcessor가
     * 큐에서 메시지를 하나씩 소비하며 호출하므로, 같은 쿠폰에 대해 동시에
     * 여러 스레드가 이 메서드를 호출하는 상황 자체가 없다(데드락 구조적 제거).
     * INSERT + 수량 증가 + 요청 상태 SUCCESS 갱신을 하나의 트랜잭션으로 묶어서
     * "발급은 됐는데 요청 상태는 아직 PENDING" 같은 불일치를 방지한다.
     */
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2))
    @Transactional
    public CouponIssueResponse saveIssue(Long requestId, Long couponId, Long userId, long remaining) {
        CouponIssue couponIssue = CouponIssue.create(couponId, userId);
        couponIssueRepository.save(couponIssue);
        LocalDateTime now = LocalDateTime.now();
        couponRepository.incrementIssuedQuantity(couponId, CouponStatus.EXHAUSTED, now);
        if (remaining == 0) {
            couponCacheService.evict(couponId);
        }
        issueRequestRepository.markSuccess(requestId, now);
        return CouponIssueResponse.from(couponIssue);
    }
}
