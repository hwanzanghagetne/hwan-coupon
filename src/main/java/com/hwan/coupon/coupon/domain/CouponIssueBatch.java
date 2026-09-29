package com.hwan.coupon.coupon.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_issue_batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private int targetCount;

    // BatchProcessor의 INSERT IGNORE가 실제로 삽입한 행 수만 누적한다.
    // "시도한 대상 수"가 아니라 "새로 발급된 수"를 뜻한다.
    @Column(nullable = false)
    private int issuedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    public static CouponIssueBatch create(Long couponId, int targetCount) {
        CouponIssueBatch batch = new CouponIssueBatch();
        batch.couponId = couponId;
        batch.targetCount = targetCount;
        batch.issuedCount = 0;
        batch.status = BatchStatus.PENDING;
        batch.requestedAt = LocalDateTime.now();
        batch.updatedAt = batch.requestedAt;
        return batch;
    }
}
