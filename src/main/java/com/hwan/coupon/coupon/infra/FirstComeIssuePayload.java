package com.hwan.coupon.coupon.infra;

public record FirstComeIssuePayload(
        Long requestId,
        Long couponId,
        Long userId,
        long remaining
) {
}
