package com.hwan.coupon.coupon.dto;

import java.time.LocalDateTime;

public record CouponIssueAcceptedResponse(
        Long couponId,
        LocalDateTime acceptedAt,
        // send()가 예외 없이 반환됐는지만 나타낸다. Publisher Confirm이 없어 브로커가
        // 실제로 메시지를 받아 저장했는지까지 확인한 값은 아니다 — "확정"이 아니라
        // "로컬에서 전송 시도가 정상적으로 끝났다"는 뜻으로 좁혀서 읽어야 한다.
        boolean sendReturned,
        String message
) {
    public static CouponIssueAcceptedResponse sent(Long couponId) {
        return new CouponIssueAcceptedResponse(couponId, LocalDateTime.now(), true, "접수되었습니다");
    }

    // 발행 자체는 시도했지만 브로커 도달 여부를 확인하지 못한 경우.
    // 당첨은 확정됐으나 "최종 발급 완료"를 의미하지 않는다 — 실제 반영 여부는 대사 스케줄러가 확정한다.
    public static CouponIssueAcceptedResponse sendUncertain(Long couponId) {
        return new CouponIssueAcceptedResponse(couponId, LocalDateTime.now(), false,
                "당첨은 확정되었으나 발행 결과가 아직 확인되지 않았습니다. 잠시 후 내 쿠폰함에서 확인해주세요");
    }
}
