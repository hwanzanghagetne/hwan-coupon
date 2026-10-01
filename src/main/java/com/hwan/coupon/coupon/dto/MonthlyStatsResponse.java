package com.hwan.coupon.coupon.dto;

public record MonthlyStatsResponse(
        String month,
        long totalIssued,
        long totalUsed
) {
}