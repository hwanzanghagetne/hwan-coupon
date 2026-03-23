package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.Coupon;
import com.hwan.coupon.coupon.CouponStatus;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record CouponCacheDto(
        Long id,
        CouponStatus status,
        LocalDateTime expiredAt,
        String issueStartTime,
        String issueEndTime,
        Integer minOrderAmount
) {
    public static CouponCacheDto from(Coupon coupon) {
        return new CouponCacheDto(
                coupon.getId(),
                coupon.getStatus(),
                coupon.getExpiredAt(),
                coupon.getIssueStartTime(),
                coupon.getIssueEndTime(),
                coupon.getMinOrderAmount()
        );
    }

    public void validateForIssue() {
        if (this.status == CouponStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.COUPON_NOT_ACTIVE);
        }
        if (LocalDateTime.now().isAfter(this.expiredAt)) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (issueStartTime != null && issueEndTime != null) {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(issueStartTime);
            LocalTime end = LocalTime.parse(issueEndTime);
            if (now.isBefore(start) || now.isAfter(end)) {
                throw new BusinessException(ErrorCode.COUPON_NOT_ACTIVE);
            }
        }
    }
}
