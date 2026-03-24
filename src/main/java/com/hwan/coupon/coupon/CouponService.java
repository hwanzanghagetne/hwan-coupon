package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.dto.*;
import com.hwan.coupon.coupon.dto.MonthlyStatsProjection;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponRedisService couponRedisService;
    private final CouponCacheService couponCacheService;
    private final TransactionTemplate transactionTemplate;

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

        if (saved.getIssueType() == IssueType.FIRST_COME && saved.getTotalQuantity() != null) {
            couponRedisService.initStock(saved.getId(), saved.getTotalQuantity());
        }

        return CouponResponse.from(saved);
    }

    public CouponIssueResponse issueCoupon(Long couponId, Long userId) {
        CouponCacheDto cached = couponCacheService.getCouponCache(couponId);
        cached.validateForIssue();

        // Redis 키 유실 시 DB 기준으로 재고 복구 (DB가 source of truth)
        if (!couponRedisService.hasStock(couponId)) {
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
            if (coupon.getTotalQuantity() != null) {
                int remaining = Math.max(0, coupon.getTotalQuantity() - coupon.getIssuedQuantity());
                couponRedisService.syncStockIfAbsent(couponId, remaining);
            }
        }

        long remaining = couponRedisService.tryIssue(couponId, userId);
        if (remaining == -1) throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        if (remaining == -2) throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);

        try {
            return transactionTemplate.execute(status -> {
                try {
                    CouponIssue couponIssue = CouponIssue.create(couponId, userId);
                    couponIssueRepository.save(couponIssue);
                    couponRepository.incrementIssuedQuantity(couponId);
                    if (remaining == 0) {
                        couponRepository.markExhausted(couponId);
                        couponCacheService.evict(couponId);
                    }
                    return CouponIssueResponse.from(couponIssue);
                } catch (DataIntegrityViolationException e) {
                    couponRedisService.rollback(couponId, userId);
                    throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
                }
            });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            couponRedisService.rollback(couponId, userId);
            throw e;
        }
    }

    @Transactional
    public CouponIssueResponse useCoupon(Long couponId, Long userId, int orderAmount) {
        CouponCacheDto cached = couponCacheService.getCouponCache(couponId);

        if (LocalDateTime.now().isAfter(cached.expiredAt())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        CouponIssue couponIssue = couponIssueRepository.findByCouponIdAndUserId(couponId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        couponIssue.use(orderAmount, cached.minOrderAmount());
        return CouponIssueResponse.from(couponIssue);
    }

    @Transactional
    public CouponIssueResponse restoreCoupon(Long couponId, Long userId) {
        CouponIssue couponIssue = couponIssueRepository.findByCouponIdAndUserId(couponId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        couponIssue.restore();
        return CouponIssueResponse.from(couponIssue);
    }

    @Transactional(readOnly = true)
    public List<MyCouponResponse> getMyCoupons(Long userId) {
        List<CouponIssue> issues = couponIssueRepository.findAllByUserId(userId);
        if (issues.isEmpty()) {
            return List.of();
        }

        List<Long> couponIds = issues.stream().map(CouponIssue::getCouponId).toList();
        Map<Long, Coupon> couponMap = couponRepository.findAllById(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));

        return issues.stream()
                .filter(issue -> couponMap.containsKey(issue.getCouponId()))
                .map(issue -> MyCouponResponse.from(issue, couponMap.get(issue.getCouponId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonthlyStatsResponse> getMonthlyStats(int year) {
        Map<String, MonthlyStatsResponse> statsMap = couponIssueRepository.findMonthlyStatsByYear(year)
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

    @Transactional(readOnly = true)
    public List<CouponResponse> getCoupons() {
        return couponRepository.findAll().stream()
                .map(CouponResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponResponse getCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        return CouponResponse.from(coupon);
    }
}
