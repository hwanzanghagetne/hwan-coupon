package com.hwan.coupon.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_issue",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    @Column(nullable = false)
    private boolean quantitySynced;

    public static CouponIssue create(Long couponId, Long userId) {
        CouponIssue issue = new CouponIssue();
        issue.couponId = couponId;
        issue.userId = userId;
        issue.status = CouponIssueStatus.ISSUED;
        issue.issuedAt = LocalDateTime.now();
        issue.quantitySynced = true;
        return issue;
    }
}
