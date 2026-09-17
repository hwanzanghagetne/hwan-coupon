package com.hwan.coupon.coupon.infra;

public record FirstComeIssuePayload(
        Long couponId,
        Long userId
) {
}
