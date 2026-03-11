package com.hwan.coupon.coupon;

import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import com.hwan.coupon.coupon.dto.CreateCouponRequest;
import com.hwan.coupon.coupon.dto.CouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

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
        return CouponResponse.from(couponRepository.save(coupon));
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
