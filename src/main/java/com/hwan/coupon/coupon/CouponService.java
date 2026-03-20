package com.hwan.coupon.coupon;

import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import com.hwan.coupon.coupon.dto.CreateCouponRequest;
import com.hwan.coupon.coupon.dto.CouponIssueResponse;
import com.hwan.coupon.coupon.dto.CouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponRedisService couponRedisService;
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
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        coupon.validateForIssue();

        long remaining = couponRedisService.tryIssue(couponId, userId);
        if (remaining == -1) throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        if (remaining == -2) throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);

        return transactionTemplate.execute(status -> {
            try {
                CouponIssue couponIssue = CouponIssue.create(couponId, userId);
                couponIssueRepository.save(couponIssue);
                couponRepository.incrementIssuedQuantity(couponId);
                if (remaining == 0) {
                    couponRepository.markExhausted(couponId);
                }
                return CouponIssueResponse.from(couponIssue);
            } catch (DataIntegrityViolationException e) {
                couponRedisService.rollback(couponId, userId);
                throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
            }
        });
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
