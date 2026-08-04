package com.hwan.coupon.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BatchDemoRequest(
        @NotNull Long couponId,
        @Min(1) @Max(5000) int userCount
) {}
