package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record CouponCacheDto(
        Long id,
        CouponStatus status,
        LocalDateTime expiredAt,
        LocalTime issueStartTime,
        LocalTime issueEndTime,
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
