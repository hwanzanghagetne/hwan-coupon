package com.hwan.coupon.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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
