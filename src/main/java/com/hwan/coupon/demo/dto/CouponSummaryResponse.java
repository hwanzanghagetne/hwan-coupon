package com.hwan.coupon.demo.dto;

import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.IssueType;

public record CouponSummaryResponse(
        Long couponId,
        String couponName,
        Integer totalQuantity,
        int issuedQuantity,
        CouponStatus couponStatus,
        IssueType issueType,
        long couponIssueCount,
        Long redisRemainingQuantity
) {}
