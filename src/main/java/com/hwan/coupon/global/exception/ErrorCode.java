package com.hwan.coupon.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Member
    MEMBER_NOT_FOUND(404, "회원을 찾을 수 없습니다"),
    EMAIL_ALREADY_EXISTS(409, "이미 사용 중인 이메일입니다"),

    // Coupon
    COUPON_INVALID_DISCOUNT_VALUE(400, "할인값은 1 이상이어야 하며, 정률 할인은 100% 이하여야 합니다"),
    COUPON_INVALID_EXPIRED_AT(400, "만료일은 현재 시각 이후여야 합니다"),
    COUPON_INVALID_ISSUE_TIME(400, "발급 시작 시간은 종료 시간보다 이전이어야 합니다"),
    COUPON_NOT_FOUND(404, "쿠폰을 찾을 수 없습니다"),
    COUPON_ISSUE_NOT_FOUND(404, "쿠폰 발급 내역을 찾을 수 없습니다"),
    COUPON_NOT_ACTIVE(400, "비활성화된 쿠폰입니다"),
    COUPON_EXHAUSTED(410, "쿠폰이 모두 소진되었습니다"),
    COUPON_ALREADY_ISSUED(409, "이미 발급된 쿠폰입니다"),
    COUPON_ALREADY_USED(409, "이미 사용된 쿠폰입니다"),
    COUPON_EXPIRED(400, "만료된 쿠폰입니다"),
    COUPON_NOT_APPLICABLE(400, "최소 주문 금액 조건을 충족하지 않습니다"),
    COUPON_RESTORE_NOT_ALLOWED(400, "복원할 수 없는 쿠폰 상태입니다"),
    COUPON_ALREADY_INACTIVE(400, "이미 비활성 상태인 쿠폰입니다"),

    // Batch
    BATCH_NOT_FOUND(404, "배치를 찾을 수 없습니다"),
    COUPON_ISSUE_TYPE_MISMATCH(400, "관리자 발급 전용 쿠폰이 아닙니다");

    private final int status;
    private final String message;
}
