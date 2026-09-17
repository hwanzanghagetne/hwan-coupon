package com.hwan.coupon.coupon.dto;

import java.time.LocalDateTime;

public record CouponIssueAcceptedResponse(
        Long couponId,
        LocalDateTime acceptedAt
) {
    public static CouponIssueAcceptedResponse of(Long couponId) {
        return new CouponIssueAcceptedResponse(couponId, LocalDateTime.now());
    }
}
