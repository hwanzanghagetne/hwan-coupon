package com.hwan.coupon.coupon.dto;

public interface MonthlyStatsProjection {
    String getMonth();
    Long getTotalIssued();
    Long getTotalUsed();
}