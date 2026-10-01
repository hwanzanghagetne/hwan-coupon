package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.service.CouponRedisService;
import com.hwan.coupon.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis 당첨과 DB 반영 결과를 주기적으로 대사해, 발행 자체가 안 됐거나 재전달이 누락된
 * 당첨 건을 재발행하는 안전장치. RabbitMQ의 ack 재전달은 메시지가 큐에 들어온 이후만
 * 보호하므로, "Redis 당첨 성공 → 발행 시도 전 종료" 구간은 이 스케줄러가 대신 메운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeIssueReconciliationScheduler {

    // INACTIVE 포함: 당첨 직후 관리자가 비활성화해도 그 당첨자는 발급을 받아야 한다.
    private static final List<CouponStatus> TARGET_STATUSES =
            List.of(CouponStatus.ACTIVE, CouponStatus.EXHAUSTED, CouponStatus.INACTIVE);

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponRedisService couponRedisService;
    private final RabbitTemplate rabbitTemplate;

    // 재고 키 유실 시 자동 재초기화는 하지 않는다 — DB 값만으로는 유실 당시 이미 당첨된
    // 사람이 있었는지 알 수 없어 초과 발급 위험이 있다.
    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        List<Coupon> targets = couponRepository.findByIssueTypeAndStatusIn(IssueType.FIRST_COME, TARGET_STATUSES);
        for (Coupon coupon : targets) {
            try {
                reconcileCoupon(coupon.getId());
            } catch (Exception e) {
                log.error("[FirstComeReconciliation] 쿠폰 대사 실패, 다음 쿠폰은 계속 진행 couponId={} error={}",
                        coupon.getId(), e.getMessage(), e);
            }
        }
    }

    private void reconcileCoupon(Long couponId) {
        Set<String> redisWinners = couponRedisService.getIssuedUserIds(couponId);
        if (redisWinners.isEmpty()) {
            return;
        }

        Set<Long> dbUserIds = new HashSet<>(couponIssueRepository.findUserIdsByCouponId(couponId));

        for (String winner : redisWinners) {
            Long userId = Long.valueOf(winner);
            if (!dbUserIds.contains(userId)) {
                log.warn("[FirstComeReconciliation] Redis 당첨은 됐지만 DB 미반영 발견, 재발행 couponId={} userId={}",
                        couponId, userId);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE,
                        RabbitMQConfig.ROUTING_KEY_FIRST_COME,
                        new FirstComeIssuePayload(couponId, userId)
                );
            }
        }
    }
}
