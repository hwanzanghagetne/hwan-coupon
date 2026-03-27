package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;

import java.time.LocalDateTime;

public record BatchIssueResponse(
        Long batchId,
        Long couponId,
        BatchStatus status,
        int targetCount,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
    public static BatchIssueResponse from(CouponIssueBatch batch) {
        return new BatchIssueResponse(
                batch.getId(),
                batch.getCouponId(),
                batch.getStatus(),
                batch.getTargetCount(),
                batch.getRequestedAt(),
                batch.getCompletedAt()
        );
    }
}