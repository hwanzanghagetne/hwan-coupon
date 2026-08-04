package com.hwan.coupon.coupon.domain;

public enum CouponIssueRequestStatus {
    PENDING,     // 접수됨, 처리 전
    PROCESSING,  // 컨슈머가 처리 중
    SUCCESS,     // 발급 완료
    FAILED       // 발급 실패
}
