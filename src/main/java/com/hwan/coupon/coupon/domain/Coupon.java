package com.hwan.coupon.coupon.domain;

import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(nullable = false)
    private int discountValue;

    private Integer totalQuantity;

    @Column(nullable = false)
    private int issuedQuantity;

    private Integer minOrderAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueType issueType;

    private LocalTime issueStartTime;

    private LocalTime issueEndTime;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Coupon create(String name, DiscountType discountType, int discountValue,
                                Integer totalQuantity, Integer minOrderAmount, IssueType issueType,
                                LocalTime issueStartTime, LocalTime issueEndTime,
                                LocalDateTime expiredAt) {
        validateDiscountValue(discountType, discountValue);
        validateExpiredAt(expiredAt);
        validateIssueTime(issueStartTime, issueEndTime);

        Coupon coupon = new Coupon();
        coupon.name = name;
        coupon.discountType = discountType;
        coupon.discountValue = discountValue;
        coupon.totalQuantity = totalQuantity;
        coupon.issuedQuantity = 0;
        coupon.minOrderAmount = minOrderAmount;
        coupon.issueType = issueType;
        coupon.issueStartTime = issueStartTime;
        coupon.issueEndTime = issueEndTime;
        coupon.expiredAt = expiredAt;
        coupon.status = CouponStatus.ACTIVE;
        coupon.createdAt = LocalDateTime.now();
        return coupon;
    }

    private static void validateDiscountValue(DiscountType discountType, int discountValue) {
        if (discountValue <= 0) {
            throw new BusinessException(ErrorCode.COUPON_INVALID_DISCOUNT_VALUE);
        }
        if (discountType == DiscountType.RATE && discountValue > 100) {
            throw new BusinessException(ErrorCode.COUPON_INVALID_DISCOUNT_VALUE);
        }
    }

    private static void validateExpiredAt(LocalDateTime expiredAt) {
        if (!expiredAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.COUPON_INVALID_EXPIRED_AT);
        }
    }

    private static void validateIssueTime(LocalTime issueStartTime, LocalTime issueEndTime) {
        if (issueStartTime != null && issueEndTime != null
                && !issueStartTime.isBefore(issueEndTime)) {
            throw new BusinessException(ErrorCode.COUPON_INVALID_ISSUE_TIME);
        }
    }
}
