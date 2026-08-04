package com.hwan.coupon.coupon.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 선착순 발급의 "접수 상태"를 추적하는 엔티티.
 *
 * coupon_issue(최종 발급 결과)와는 다른 개념이다. Redis 당첨 직후 DB에 바로
 * 쓰지 않고 이 요청을 PENDING으로 저장한 뒤 RabbitMQ에 발행하고,
 * FirstComeIssueProcessor가 순차 처리하며 SUCCESS/FAILED로 갱신한다.
 */
@Entity
@Table(name = "coupon_issue_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueRequestStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    private String failureReason;

    public static CouponIssueRequest create(Long couponId, Long userId) {
        CouponIssueRequest request = new CouponIssueRequest();
        request.couponId = couponId;
        request.userId = userId;
        request.status = CouponIssueRequestStatus.PENDING;
        request.requestedAt = LocalDateTime.now();
        request.updatedAt = request.requestedAt;
        return request;
    }
}
