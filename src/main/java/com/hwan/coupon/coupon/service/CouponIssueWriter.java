package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.infra.CouponIssueBulkInsertSupport;
import com.hwan.coupon.coupon.infra.FirstComeIssuePayload;
import com.hwan.coupon.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 선착순 발급 당첨자를 짧은 주기로 모아 DB에 배치 반영하는 컴포넌트.
 *
 * 개별 요청 단위 상태(PENDING/PROCESSING/SUCCESS/FAILED) 추적은 없다. 한 배치에
 * 여러 쿠폰의 당첨자가 섞여 있을 수 있어 couponId별로 묶어서 반영하고, 쿠폰별로
 * 실제 삽입된 행 수만큼만 issuedQuantity를 반영한다(INSERT IGNORE로 중복은
 * 조용히 스킵되므로 재전달돼도 안전하다).
 */
@Component
@RequiredArgsConstructor
public class CouponIssueWriter {

    private final CouponRepository couponRepository;
    private final CouponIssueBulkInsertSupport bulkInsertSupport;
    private final CouponCacheService couponCacheService;

    @Transactional
    public int saveIssueBatch(List<FirstComeIssuePayload> payloads) {
        if (payloads.isEmpty()) {
            return 0;
        }

        Map<Long, List<Long>> userIdsByCoupon = payloads.stream()
                .collect(Collectors.groupingBy(
                        FirstComeIssuePayload::couponId,
                        Collectors.mapping(FirstComeIssuePayload::userId, Collectors.toList())
                ));

        LocalDateTime now = LocalDateTime.now();
        int totalInserted = 0;

        for (Map.Entry<Long, List<Long>> entry : userIdsByCoupon.entrySet()) {
            Long couponId = entry.getKey();
            List<Long> userIds = entry.getValue();

            int inserted = bulkInsertSupport.insertIgnore(couponId, userIds);
            if (inserted > 0) {
                couponRepository.incrementIssuedQuantityByAndMarkExhausted(couponId, inserted, CouponStatus.EXHAUSTED, now);
            }
            couponCacheService.evict(couponId);
            totalInserted += inserted;
        }

        return totalInserted;
    }
}
