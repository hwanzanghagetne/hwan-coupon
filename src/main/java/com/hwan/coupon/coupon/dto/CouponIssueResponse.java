package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.CouponIssue;
import com.hwan.coupon.coupon.CouponIssueStatus;

import java.time.LocalDateTime;

public record CouponIssueResponse(
        Long issueId,
        Long couponId,
        Long userId,
        CouponIssueStatus status,
        LocalDateTime issuedAt
) {
    public static CouponIssueResponse from(CouponIssue issue) {
        return new CouponIssueResponse(
                issue.getId(),
                issue.getCouponId(),
                issue.getUserId(),
                issue.getStatus(),
                issue.getIssuedAt()
        );
    }
}