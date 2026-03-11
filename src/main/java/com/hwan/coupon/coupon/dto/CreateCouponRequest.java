package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.DiscountType;
import com.hwan.coupon.coupon.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateCouponRequest(

        @NotBlank
        String name,

        @NotNull
        DiscountType discountType,

        @NotNull
        Integer discountValue,

        Integer totalQuantity,      // null이면 무제한

        Integer minOrderAmount,     // null이면 조건 없음

        @NotNull
        IssueType issueType,

        String issueStartTime,      // null이면 시간 제한 없음

        String issueEndTime,        // null이면 시간 제한 없음

        @NotNull
        LocalDateTime expiredAt
) {}
