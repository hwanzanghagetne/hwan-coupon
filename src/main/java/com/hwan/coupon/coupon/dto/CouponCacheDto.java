package com.hwan.coupon.coupon.dto;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.IssueType;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record CouponCacheDto(
        CouponStatus status,
        IssueType issueType,
        LocalDateTime expiredAt,
        LocalTime issueStartTime,
        LocalTime issueEndTime
) {
    public static CouponCacheDto from(Coupon coupon) {
        return new CouponCacheDto(
                coupon.getStatus(),
                coupon.getIssueType(),
                coupon.getExpiredAt(),
                coupon.getIssueStartTime(),
                coupon.getIssueEndTime()
        );
    }
}
