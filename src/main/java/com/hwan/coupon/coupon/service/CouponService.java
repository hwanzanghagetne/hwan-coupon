package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssue;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;

import com.hwan.coupon.coupon.dto.CouponCacheDto;
import com.hwan.coupon.coupon.dto.CouponIssueResponse;
import com.hwan.coupon.coupon.dto.CouponResponse;
import com.hwan.coupon.coupon.dto.CreateCouponRequest;
import com.hwan.coupon.coupon.dto.MonthlyStatsProjection;
import com.hwan.coupon.coupon.dto.MonthlyStatsResponse;
import com.hwan.coupon.coupon.dto.MyCouponResponse;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    private static final long REDIS_RESULT_EXHAUSTED      = -1L;
    private static final long REDIS_RESULT_ALREADY_ISSUED = -2L;

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponRedisService couponRedisService;
    private final CouponCacheService couponCacheService;
    private final CouponIssueWriter couponIssueWriter;

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
        Coupon saved = couponIssueWriter.saveCoupon(coupon);
        log.info("쿠폰 생성 완료 couponId={} name={} issueType={}", saved.getId(), saved.getName(), saved.getIssueType());

        if (saved.getIssueType() == IssueType.FIRST_COME && saved.getTotalQuantity() != null) {
            couponRedisService.initStock(saved.getId(), saved.getTotalQuantity());
            log.info("Redis 재고 초기화 couponId={} totalQuantity={}", saved.getId(), saved.getTotalQuantity());
        }

        return CouponResponse.from(saved);
    }

    public CouponIssueResponse issueCoupon(Long couponId, Long userId) {
        CouponCacheDto cached = couponCacheService.getCouponCache(couponId);
        validateCouponForIssue(cached);

        if (!couponRedisService.hasStock(couponId)) {
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
            if (coupon.getTotalQuantity() != null) {
                int remaining = Math.max(0, coupon.getTotalQuantity() - coupon.getIssuedQuantity());
                couponRedisService.syncStockIfAbsent(couponId, remaining);
            }
        }

        long remaining = couponRedisService.tryIssue(couponId, userId);
        if (remaining == REDIS_RESULT_EXHAUSTED) {
            log.warn("쿠폰 소진 couponId={} userId={}", couponId, userId);
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }
        if (remaining == REDIS_RESULT_ALREADY_ISSUED) {
            log.warn("중복 발급 시도 couponId={} userId={}", couponId, userId);
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
        log.info("쿠폰 발급 성공 couponId={} userId={} remaining={}", couponId, userId, remaining);

        try {
            return couponIssueWriter.saveIssue(couponId, userId, remaining);
        } catch (DataIntegrityViolationException e) {
            couponRedisService.rollbackStockOnly(couponId);
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            couponRedisService.rollback(couponId, userId);
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
        LocalDateTime end   = LocalDateTime.of(year + 1, 1, 1, 0, 0, 0);

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
        int updated = couponRepository.markInactive(couponId, CouponStatus.INACTIVE, CouponStatus.ACTIVE);

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

    private void validateCouponForIssue(CouponCacheDto cached) {
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
