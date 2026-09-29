package com.hwan.coupon.coupon.repository;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssueStatus;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.IssueType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + :count, c.updatedAt = :now WHERE c.id = :couponId")
    void incrementIssuedQuantityBy(@Param("couponId") Long couponId, @Param("count") int count, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Coupon c
               SET c.issuedQuantity = c.issuedQuantity + :count,
                   c.status = CASE
                       WHEN c.totalQuantity IS NOT NULL AND c.issuedQuantity + :count >= c.totalQuantity
                           THEN :exhaustedStatus
                       ELSE c.status
                   END,
                   c.updatedAt = :now
             WHERE c.id = :couponId
            """)
    void incrementIssuedQuantityByAndMarkExhausted(@Param("couponId") Long couponId,
                                                    @Param("count") int count,
                                                    @Param("exhaustedStatus") CouponStatus exhaustedStatus,
                                                    @Param("now") LocalDateTime now);

    List<Coupon> findByIssueTypeAndStatusIn(IssueType issueType, List<CouponStatus> statuses);

    @Query("SELECT c.id FROM Coupon c WHERE c.status = :status AND c.expiredAt < :now")
    List<Long> findExpiredActiveCouponIds(@Param("status") CouponStatus status, @Param("now") LocalDateTime now);

    // 쿠폰 상태(EXHAUSTED, INACTIVE 등)와 무관하게 expiredAt만으로 판단한다.
    @Query("""
            SELECT DISTINCT c.id FROM Coupon c
             WHERE c.expiredAt < :now
               AND EXISTS (
                   SELECT 1 FROM CouponIssue ci
                    WHERE ci.couponId = c.id AND ci.status = :issuedStatus
               )
            """)
    List<Long> findExpiredCouponIds(@Param("now") LocalDateTime now, @Param("issuedStatus") CouponIssueStatus issuedStatus);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.status = :status, c.updatedAt = :now WHERE c.id IN :ids AND c.status = :currentStatus")
    int markInactiveByIds(@Param("ids") List<Long> ids, @Param("status") CouponStatus status, @Param("currentStatus") CouponStatus currentStatus, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.status = :status, c.updatedAt = :now WHERE c.id = :couponId AND c.status = :currentStatus")
    int markInactive(@Param("couponId") Long couponId, @Param("status") CouponStatus status, @Param("currentStatus") CouponStatus currentStatus, @Param("now") LocalDateTime now);
}
