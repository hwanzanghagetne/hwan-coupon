package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssue;
import com.hwan.coupon.coupon.domain.CouponIssueStatus;
import com.hwan.coupon.coupon.domain.DiscountType;

import java.time.LocalDateTime;

public record MyCouponResponse(
        Long issueId,
        Long couponId,
        String couponName,
        DiscountType discountType,
        int discountValue,
        Integer minOrderAmount,
        LocalDateTime expiredAt,
        CouponIssueStatus status,
        LocalDateTime issuedAt,
        LocalDateTime usedAt
) {
    public static MyCouponResponse from(CouponIssue issue, Coupon coupon) {
        return new MyCouponResponse(
                issue.getId(),
                issue.getCouponId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt(),
                issue.getStatus(),
                issue.getIssuedAt(),
                issue.getUsedAt()
        );
    }
}