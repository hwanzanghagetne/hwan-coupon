package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponStatus;

import java.time.LocalDateTime;

public record CouponCacheDto(
        Long id,
        CouponStatus status,
        LocalDateTime expiredAt,
        String issueStartTime,
        String issueEndTime,
        Integer minOrderAmount
) {
    public static CouponCacheDto from(Coupon coupon) {
        return new CouponCacheDto(
                coupon.getId(),
                coupon.getStatus(),
                coupon.getExpiredAt(),
                coupon.getIssueStartTime(),
                coupon.getIssueEndTime(),
                coupon.getMinOrderAmount()
        );
    }
}
