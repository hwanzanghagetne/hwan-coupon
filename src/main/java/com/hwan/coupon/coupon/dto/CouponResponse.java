package com.hwan.coupon.coupon.dto;

import tools.jackson.annotation.JsonFormat;
import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CouponResponse(
        Long id,
        String name,
        DiscountType discountType,
        int discountValue,
        Integer totalQuantity,
        int issuedQuantity,
        Integer minOrderAmount,
        IssueType issueType,
        @JsonFormat(pattern = "HH:mm") LocalTime issueStartTime,
        @JsonFormat(pattern = "HH:mm") LocalTime issueEndTime,
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
