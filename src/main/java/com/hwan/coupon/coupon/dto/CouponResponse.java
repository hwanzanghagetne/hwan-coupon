package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.Coupon;
import com.hwan.coupon.coupon.CouponStatus;
import com.hwan.coupon.coupon.DiscountType;
import com.hwan.coupon.coupon.IssueType;
import java.time.LocalDateTime;

public record CouponResponse(
        Long id,
        String name,
        DiscountType discountType,
        int discountValue,
        Integer totalQuantity,
        int issuedQuantity,
        Integer minOrderAmount,
        IssueType issueType,
        String issueStartTime,
        String issueEndTime,
        LocalDateTime expiredAt,
        CouponStatus status,
        LocalDateTime createdAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getTotalQuantity(),
                coupon.getIssuedQuantity(),
                coupon.getMinOrderAmount(),
                coupon.getIssueType(),
                coupon.getIssueStartTime(),
                coupon.getIssueEndTime(),
                coupon.getExpiredAt(),
                coupon.getStatus(),
                coupon.getCreatedAt()
        );
    }
}
