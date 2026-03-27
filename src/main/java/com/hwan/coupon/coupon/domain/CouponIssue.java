package com.hwan.coupon.coupon.domain;

import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_issue",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"}),
        indexes = @Index(name = "idx_issued_at_status", columnList = "issued_at, status"))
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

    public void use(int orderAmount, Integer minOrderAmount) {
        if (this.status == CouponIssueStatus.USED) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
        }
        if (this.status == CouponIssueStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE);
        }
        this.status = CouponIssueStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void restore() {
        if (this.status != CouponIssueStatus.USED) {
            throw new BusinessException(ErrorCode.COUPON_RESTORE_NOT_ALLOWED);
        }
        this.status = CouponIssueStatus.ISSUED;
        this.usedAt = null;
    }
}
