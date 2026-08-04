package com.hwan.coupon.coupon.repository;

import com.hwan.coupon.coupon.domain.CouponIssueRequest;
import com.hwan.coupon.coupon.domain.CouponIssueRequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Long> {

    long countByCouponIdAndStatusIn(Long couponId, Collection<CouponIssueRequestStatus> statuses);

    // PENDING 상태일 때만 PROCESSING으로 전환 — 반환값이 0이면 메시지가 이미 처리(선점)된 것(재전달 멱등 처리)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueRequest r SET r.status = :to, r.updatedAt = :now WHERE r.id = :id AND r.status = :from")
    int updateStatusIfMatch(@Param("id") Long id,
                             @Param("from") CouponIssueRequestStatus from,
                             @Param("to") CouponIssueRequestStatus to,
                             @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueRequest r SET r.status = 'SUCCESS', r.completedAt = :now, r.updatedAt = :now WHERE r.id = :id")
    void markSuccess(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueRequest r SET r.status = 'FAILED', r.failureReason = :reason, r.completedAt = :now, r.updatedAt = :now WHERE r.id = :id")
    void markFailed(@Param("id") Long id, @Param("reason") String reason, @Param("now") LocalDateTime now);
}
