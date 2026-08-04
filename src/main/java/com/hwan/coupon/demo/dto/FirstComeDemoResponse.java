package com.hwan.coupon.demo.dto;

public record FirstComeDemoResponse(
        Long couponId,
        int requestUserCount,
        int couponQuantity,
        int threadCount,
        int successCount,
        int exhaustedCount,
        int duplicateCount,
        int otherFailureCount,
        long issuedRows,
        int issuedQuantity,
        long durationMs
) {}
