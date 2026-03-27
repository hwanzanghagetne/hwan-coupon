package com.hwan.coupon.coupon.domain;

public enum CouponStatus {
    ACTIVE,     // 활성 (발급 가능)
    INACTIVE,   // 비활성 (관리자가 강제 종료)
    EXHAUSTED   // 소진 (수량 모두 소진)
}
