package com.hwan.coupon.demo.dto;

import com.hwan.coupon.coupon.domain.BatchStatus;

import java.time.LocalDateTime;

public record BatchSummaryResponse(
        Long batchId,
        Long couponId,
        BatchStatus batchStatus,
        int targetCount,
        LocalDateTime completedAt,
        long couponIssueCountForCoupon,
        int issuedQuantity
) {}
