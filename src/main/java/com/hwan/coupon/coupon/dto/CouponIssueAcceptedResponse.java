package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.CouponIssueRequest;
import com.hwan.coupon.coupon.domain.CouponIssueRequestStatus;

import java.time.LocalDateTime;

public record CouponIssueAcceptedResponse(
        Long requestId,
        Long couponId,
        CouponIssueRequestStatus status,
        LocalDateTime requestedAt
) {
    public static CouponIssueAcceptedResponse from(CouponIssueRequest request) {
        return new CouponIssueAcceptedResponse(
                request.getId(),
                request.getCouponId(),
                request.getStatus(),
                request.getRequestedAt()
        );
    }
}
