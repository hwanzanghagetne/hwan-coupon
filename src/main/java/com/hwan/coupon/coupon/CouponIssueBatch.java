package com.hwan.coupon.coupon;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    public static CouponIssueBatch create(Long couponId, int targetCount) {
        CouponIssueBatch batch = new CouponIssueBatch();
        batch.couponId = couponId;
        batch.targetCount = targetCount;
        batch.status = BatchStatus.PENDING;
        batch.requestedAt = LocalDateTime.now();
        return batch;
    }

    public void markProcessing() {
        this.status = BatchStatus.PROCESSING;
    }

    public void markDone() {
        this.status = BatchStatus.DONE;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = BatchStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
