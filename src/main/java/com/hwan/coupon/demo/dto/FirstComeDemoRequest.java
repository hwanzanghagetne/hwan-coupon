package com.hwan.coupon.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FirstComeDemoRequest(
        @NotNull Long couponId,
        @Min(1) @Max(2000) int requestUserCount,
        @Min(1) @Max(300) int threadCount
) {}
