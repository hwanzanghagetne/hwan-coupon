package com.hwan.coupon.coupon.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UseCouponRequest(
        @PositiveOrZero(message = "주문 금액은 0 이상이어야 합니다")
        int orderAmount
) {
}