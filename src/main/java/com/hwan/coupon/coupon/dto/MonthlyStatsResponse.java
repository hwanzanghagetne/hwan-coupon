package com.hwan.coupon.coupon.dto;

public record MonthlyStatsResponse(
        String month,
        long totalIssued,
        long totalUsed
) {
    public static MonthlyStatsResponse empty(String month) {
        return new MonthlyStatsResponse(month, 0L, 0L);
    }
}