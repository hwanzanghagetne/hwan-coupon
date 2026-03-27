package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "시간 형식은 HH:mm이어야 합니다")
        String issueStartTime,      // null이면 시간 제한 없음

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "시간 형식은 HH:mm이어야 합니다")
        String issueEndTime,        // null이면 시간 제한 없음

        @NotNull
        LocalDateTime expiredAt
) {
    @AssertTrue(message = "발급 시작/종료 시간은 둘 다 입력하거나 둘 다 비워야 합니다")
    public boolean isIssueTimeValid() {
        return (issueStartTime == null) == (issueEndTime == null);
    }
}
