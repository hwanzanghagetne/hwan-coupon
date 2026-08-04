package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.CouponIssueRequest;
import com.hwan.coupon.coupon.domain.CouponIssueRequestStatus;

import java.time.LocalDateTime;

public record CouponIssueRequestStatusResponse(
        Long requestId,
        Long couponId,
        CouponIssueRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        String failureReason
) {
    public static CouponIssueRequestStatusResponse from(CouponIssueRequest request) {
        return new CouponIssueRequestStatusResponse(
                request.getId(),
                request.getCouponId(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getCompletedAt(),
                request.getFailureReason()
        );
    }
}
