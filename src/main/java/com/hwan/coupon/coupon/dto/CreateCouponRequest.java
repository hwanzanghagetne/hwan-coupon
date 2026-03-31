package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

        LocalTime issueStartTime,   // null이면 시간 제한 없음

        LocalTime issueEndTime,     // null이면 시간 제한 없음

        @NotNull
        LocalDateTime expiredAt
) {
    @AssertTrue(message = "발급 시작/종료 시간은 둘 다 입력하거나 둘 다 비워야 합니다")
    public boolean isIssueTimePresenceValid() {
        return (issueStartTime == null) == (issueEndTime == null);
    }

    @AssertTrue(message = "발급 시작 시간은 종료 시간보다 이전이어야 합니다")
    public boolean isIssueTimeOrderValid() {
        if (issueStartTime == null || issueEndTime == null) return true;
        return issueStartTime.isBefore(issueEndTime);
    }
}
