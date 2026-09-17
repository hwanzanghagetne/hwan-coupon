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
 * Redis 당첨 판정과 DB 반영 결과를 주기적으로 대사(reconciliation)해서, 애플리케이션 종료 등으로
 * RabbitMQ 발행 자체가 안 됐거나 재전달이 누락된 당첨 건을 찾아 다시 발행하는 최종 안전장치.
 *
 * ack 기반 재전달은 "큐에 들어온 이후" 구간만 보호한다. "Redis 당첨 성공 → 발행 시도 전 종료"
 * 구간은 큐가 보호해줄 수 없어서 별도로 필요하다.
 *
 * 정상 처리(최대 200ms 배치 주기)라면 대부분의 당첨 건은 이 스케줄러가 돌기 전에 이미
 * coupon_issue에 반영돼 있으므로, 실행 주기가 넉넉하면(1분) 정상 처리 중인 건을 오탐할
 * 가능성은 낮다. 오탐이 나더라도 재발행된 메시지는 INSERT IGNORE로 조용히 스킵되므로 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeIssueReconciliationScheduler {

    private static final List<CouponStatus> TARGET_STATUSES = List.of(CouponStatus.ACTIVE, CouponStatus.EXHAUSTED);

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponRedisService couponRedisService;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        List<Coupon> targets = couponRepository.findByIssueTypeAndStatusIn(IssueType.FIRST_COME, TARGET_STATUSES);
        for (Coupon coupon : targets) {
            reconcileCoupon(coupon.getId());
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
