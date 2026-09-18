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

    // INACTIVE도 포함하는 이유: 당첨된 직후 관리자가 쿠폰을 비활성화해도, 그 당첨자는
    // 여전히 발급을 받아야 한다. 쿠폰 상태가 대사 대상에서 그 요청을 제외할 이유는 아니다.
    private static final List<CouponStatus> TARGET_STATUSES =
            List.of(CouponStatus.ACTIVE, CouponStatus.EXHAUSTED, CouponStatus.INACTIVE);

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponRedisService couponRedisService;
    private final RabbitTemplate rabbitTemplate;

    // 재고 키가 없을 때 "발급 이력이 0건이니 안전하다"는 판단으로 자동 재초기화를 시도했던
    // 적이 있었는데, 이 조건은 실제로 안전을 보장하지 않는다는 게 밝혀져 제거했다. DB
    // issuedQuantity==0은 "아직 DB에 반영된 게 없다"는 뜻일 뿐 "Redis에서 아무도 당첨된 적
    // 없다"는 뜻이 아니다 — 당첨 판정(Redis)과 DB 반영 사이에는 큐 대기 시간만큼 지연이
    // 있어서, 그 사이 Redis가 유실되면 이미 당첨된 사람이 있어도 issuedQuantity는 여전히
    // 0으로 보인다. 이 상태에서 전체 재고를 재배정하면 그 당첨자 몫까지 겹쳐서 초과 발급될
    // 수 있다. 새 쿠폰의 최초 초기화는 이제 CouponService.createCoupon()의 DB 트랜잭션
    // 안에서 처리하므로(실패 시 생성 자체가 롤백됨), 이미 존재하던 쿠폰의 재고 유실은
    // 자동 복구하지 않고 발급 거절(503)로 막은 뒤 운영자가 판단하도록 남긴다.
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
