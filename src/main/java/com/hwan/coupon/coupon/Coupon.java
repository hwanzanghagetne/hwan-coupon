package com.hwan.coupon.coupon;

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

    private String issueStartTime;

    private String issueEndTime;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    public static Coupon create(String name, DiscountType discountType, int discountValue,
                                Integer totalQuantity, Integer minOrderAmount, IssueType issueType,
                                String issueStartTime, String issueEndTime,
                                LocalDateTime expiredAt) {
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
}
