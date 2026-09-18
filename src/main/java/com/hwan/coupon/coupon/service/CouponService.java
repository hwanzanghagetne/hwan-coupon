package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssue;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.dto.CouponCacheDto;
import com.hwan.coupon.coupon.dto.CouponIssueAcceptedResponse;
import com.hwan.coupon.coupon.dto.CouponIssueResponse;
import com.hwan.coupon.coupon.dto.CouponResponse;
import com.hwan.coupon.coupon.dto.CreateCouponRequest;
import com.hwan.coupon.coupon.dto.MonthlyStatsProjection;
import com.hwan.coupon.coupon.dto.MonthlyStatsResponse;
import com.hwan.coupon.coupon.dto.MyCouponResponse;
import com.hwan.coupon.coupon.infra.FirstComeIssuePayload;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.global.config.RabbitMQConfig;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private static final long REDIS_RESULT_EXHAUSTED = -1L;
    private static final long REDIS_RESULT_ALREADY_ISSUED = -2L;

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponRedisService couponRedisService;
    private final CouponCacheService couponCacheService;
    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;

    @Value("${coupon.issue.timing-log-enabled:false}")
    private boolean issueTimingLogEnabled;

    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request) {
        Coupon coupon = Coupon.create(
                request.name(),
                request.discountType(),
                request.discountValue(),
                request.totalQuantity(),
                request.minOrderAmount(),
                request.issueType(),
                request.issueStartTime(),
                request.issueEndTime(),
                request.expiredAt()
        );
        Coupon saved = couponRepository.save(coupon);
        log.info("쿠폰 생성 완료 couponId={} name={} issueType={}", saved.getId(), saved.getName(), saved.getIssueType());

        // GenerationType.IDENTITY라 save() 시점에 이미 ID가 확정되므로, 커밋을 기다리지 않고
        // 같은 트랜잭션 안에서 바로 Redis 재고를 초기화한다. 이 호출이 실패하면 예외가 그대로
        // 전파되어 쿠폰 생성 자체가 롤백된다 — "쿠폰은 DB에 있는데 재고 키는 없어서 영구히
        // 거절되는" 상태가 생기지 않는다.
        // 다만 이게 DB와 Redis를 하나의 원자적 트랜잭션으로 묶어주는 건 아니다. 이 호출이
        // 성공한 직후, DB 커밋 자체가(드물지만) 실패하는 경우엔 Redis에 안 쓰이는 키가
        // 남을 수 있다 — 존재하지 않는 couponId라 아무도 조회하지 않는 죽은 데이터라
        // 무해하다. 반대로 이 호출 이후 어떤 이유로든 재고 키가 사라지면(운영 중 Redis
        // 장애 등) 그때는 발급을 거절(503)하는 것이 안전장치다.
        if (saved.getIssueType() == IssueType.FIRST_COME) {
            couponRedisService.initStock(saved.getId(), saved.getTotalQuantity());
            log.info("Redis 재고 초기화 couponId={} totalQuantity={}", saved.getId(), saved.getTotalQuantity());
        }

        return CouponResponse.from(saved);
    }

    public CouponIssueAcceptedResponse issueCoupon(Long couponId, Long userId) {
        long requestStart = System.nanoTime();
        long cacheValidatedAt = requestStart;
        long stockReadyAt = requestStart;
        long redisCheckedAt = requestStart;
        long requestSavedAt = requestStart;
        long publishedAt = requestStart;
        String result = "UNKNOWN";

        try {
            CouponCacheDto cached = couponCacheService.getCouponCache(couponId);
            validateCouponForIssue(cached);
            cacheValidatedAt = System.nanoTime();

            // Redis 재고 키가 없으면(장애/재시작으로 데이터 유실) DB 기준으로 자동 복구하지 않고
            // 발급을 거절한다. 유실 시엔 재고 카운터뿐 아니라 중복 발급 방지용 당첨자 집합도
            // 함께 사라지는데, DB만으로는 그 집합을 온전히 복원할 수 없다(발행 직전 죽어서
            // Redis에만 존재했던 당첨 기록은 DB에도 큐에도 흔적이 없다). 잘못된 자동 복구로
            // 조용히 재고를 잘못 나눠주기보다, 명시적으로 거절하고 복구는 별도 절차로 남긴다.
            if (!couponRedisService.hasStock(couponId)) {
                log.error("[재고 확인 불가] Redis 재고 키 없음 couponId={} — 발급 거절", couponId);
                throw new BusinessException(ErrorCode.COUPON_STOCK_TEMPORARILY_UNAVAILABLE);
            }
            stockReadyAt = System.nanoTime();

            // Redis 당첨 판정 전에 메시지 변환을 먼저 끝낸다. 변환 자체가 실패하는 경우
            // (거의 없겠지만) 이 시점엔 아직 아무것도 당첨되지 않았으므로 롤백할 것이 없다.
            Message message = messageConverter.toMessage(new FirstComeIssuePayload(couponId, userId), new MessageProperties());

            long remaining = couponRedisService.tryIssue(couponId, userId);
            redisCheckedAt = System.nanoTime();
            if (remaining == REDIS_RESULT_EXHAUSTED) {
                result = "EXHAUSTED";
                log.warn("쿠폰 소진 couponId={} userId={}", couponId, userId);
                throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
            }
            if (remaining == REDIS_RESULT_ALREADY_ISSUED) {
                result = "DUPLICATE";
                log.warn("중복 발급 시도 couponId={} userId={}", couponId, userId);
                throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
            }
            log.info("선착순 당첨 확정 couponId={} userId={} remaining={}", couponId, userId, remaining);
            requestSavedAt = System.nanoTime();

            CouponIssueAcceptedResponse response;
            try {
                rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_FIRST_COME, message);
                result = "ACCEPTED";
                log.info("선착순 발급 요청 접수 couponId={} userId={}", couponId, userId);
                response = CouponIssueAcceptedResponse.sent(couponId);
            } catch (AmqpException e) {
                // 연결 오류/타임아웃 등은 브로커가 실제로 메시지를 받았는지 확정할 수 없다.
                // 여기서 Redis 당첨을 롤백하면, 실제로는 브로커가 받아서 이후 정상 처리되는
                // 경우와 겹쳐 이중 발급(재고 초과) 위험이 더 커진다. 롤백하지 않고 "미확정"으로
                // 응답하며, 최종 결과 확정은 FirstComeIssueReconciliationScheduler에 맡긴다.
                result = "PUBLISH_UNCERTAIN";
                log.error("[PublishUncertain] 선착순 발급 메시지 발행 결과 불확실 couponId={} userId={} error={}",
                        couponId, userId, e.getMessage(), e);
                response = CouponIssueAcceptedResponse.sendUncertain(couponId);
            }
            publishedAt = System.nanoTime();

            logIssueTiming(result, couponId, userId, requestStart, cacheValidatedAt, stockReadyAt, redisCheckedAt, requestSavedAt, publishedAt);
            return response;
        } catch (RuntimeException e) {
            logIssueTiming(result, couponId, userId, requestStart, cacheValidatedAt, stockReadyAt, redisCheckedAt, requestSavedAt, System.nanoTime());
            throw e;
        }
    }

    @Transactional
    public CouponIssueResponse useCoupon(Long couponId, Long userId, int orderAmount) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        if (LocalDateTime.now().isAfter(coupon.getExpiredAt())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        CouponIssue couponIssue = couponIssueRepository.findByCouponIdAndUserId(couponId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_ISSUE_NOT_FOUND));

        couponIssue.use(orderAmount, coupon.getMinOrderAmount());
        return CouponIssueResponse.from(couponIssue);
    }

    @Transactional
    public CouponIssueResponse restoreCoupon(Long couponId, Long userId) {
        CouponIssue couponIssue = couponIssueRepository.findByCouponIdAndUserId(couponId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_ISSUE_NOT_FOUND));

        couponIssue.restore();
        return CouponIssueResponse.from(couponIssue);
    }

    @Transactional(readOnly = true)
    public Page<MyCouponResponse> getMyCoupons(Long userId, Pageable pageable) {
        Page<CouponIssue> issuePage = couponIssueRepository.findAllByUserId(userId, pageable);

        List<Long> couponIds = issuePage.getContent().stream().map(CouponIssue::getCouponId).toList();
        Map<Long, Coupon> couponMap = couponRepository.findAllById(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));

        return issuePage.map(issue -> MyCouponResponse.from(issue, couponMap.get(issue.getCouponId())));
    }

    @Transactional(readOnly = true)
    public List<MonthlyStatsResponse> getMonthlyStats(int year) {
        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(year + 1, 1, 1, 0, 0, 0);

        Map<String, MonthlyStatsResponse> statsMap = couponIssueRepository.findMonthlyStatsByYear(start, end)
                .stream()
                .collect(Collectors.toMap(
                        MonthlyStatsProjection::getMonth,
                        p -> new MonthlyStatsResponse(p.getMonth(), p.getTotalIssued(), p.getTotalUsed())
                ));

        return IntStream.rangeClosed(1, 12)
                .mapToObj(month -> String.format("%d-%02d", year, month))
                .map(key -> statsMap.getOrDefault(key, MonthlyStatsResponse.empty(key)))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deactivateCoupon(Long couponId) {
        int updated = couponRepository.markInactive(couponId, CouponStatus.INACTIVE, CouponStatus.ACTIVE, LocalDateTime.now());

        if (updated == 0) {
            couponRepository.findById(couponId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
            throw new BusinessException(ErrorCode.COUPON_ALREADY_INACTIVE);
        }

        couponCacheService.evict(couponId);
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> getCoupons(Pageable pageable) {
        return couponRepository.findAll(pageable).map(CouponResponse::from);
    }

    @Transactional(readOnly = true)
    public CouponResponse getCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        return CouponResponse.from(coupon);
    }

    private void logIssueTiming(
            String result,
            Long couponId,
            Long userId,
            long requestStart,
            long cacheValidatedAt,
            long stockReadyAt,
            long redisCheckedAt,
            long requestSavedAt,
            long endAt
    ) {
        if (!issueTimingLogEnabled) {
            return;
        }

        log.info(
                "issueCouponTiming result={} couponId={} userId={} cacheValidateMs={} stockSyncMs={} redisTryMs={} requestSaveMs={} publishMs={} totalMs={}",
                result,
                couponId,
                userId,
                elapsedMillis(requestStart, cacheValidatedAt),
                elapsedMillis(cacheValidatedAt, stockReadyAt),
                elapsedMillis(stockReadyAt, redisCheckedAt),
                elapsedMillis(redisCheckedAt, requestSavedAt),
                elapsedMillis(requestSavedAt, endAt),
                elapsedMillis(requestStart, endAt)
        );
    }

    private long elapsedMillis(long startNanos, long endNanos) {
        return Math.max(0L, (endNanos - startNanos) / 1_000_000L);
    }

    private void validateCouponForIssue(CouponCacheDto cached) {
        if (cached.issueType() != IssueType.FIRST_COME) {
            throw new BusinessException(ErrorCode.COUPON_NOT_DIRECTLY_ISSUABLE);
        }
        if (cached.status() == CouponStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.COUPON_NOT_ACTIVE);
        }
        if (cached.status() == CouponStatus.EXHAUSTED) {
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }
        if (LocalDateTime.now().isAfter(cached.expiredAt())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (cached.issueStartTime() != null && cached.issueEndTime() != null) {
            LocalTime now = LocalTime.now();
            if (now.isBefore(cached.issueStartTime()) || now.isAfter(cached.issueEndTime())) {
                throw new BusinessException(ErrorCode.COUPON_NOT_ACTIVE);
            }
        }
    }
}