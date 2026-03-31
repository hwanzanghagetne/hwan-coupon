package com.hwan.coupon.coupon.repository;

import com.hwan.coupon.coupon.domain.CouponIssue;
import com.hwan.coupon.coupon.domain.CouponIssueStatus;

import com.hwan.coupon.coupon.dto.MonthlyStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

    Optional<CouponIssue> findByCouponIdAndUserId(Long couponId, Long userId);

    long countByCouponId(Long couponId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CouponIssue ci WHERE ci.couponId = :couponId")
    void deleteByCouponId(@Param("couponId") Long couponId);

    Page<CouponIssue> findAllByUserId(Long userId, Pageable pageable);

    @Query(value = """
            SELECT DATE_FORMAT(issued_at, '%Y-%m')                               AS month,
                   COUNT(*)                                                       AS totalIssued,
                   COALESCE(SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END), 0) AS totalUsed
            FROM coupon_issue
            WHERE issued_at >= :start AND issued_at < :end
            GROUP BY DATE_FORMAT(issued_at, '%Y-%m')
            ORDER BY DATE_FORMAT(issued_at, '%Y-%m')
            """, nativeQuery = true)
    List<MonthlyStatsProjection> findMonthlyStatsByYear(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssue ci SET ci.status = :toStatus WHERE ci.couponId IN :couponIds AND ci.status = :fromStatus")
    int expireIssuedByCouponIds(@Param("couponIds") List<Long> couponIds,
                                @Param("toStatus") CouponIssueStatus toStatus,
                                @Param("fromStatus") CouponIssueStatus fromStatus);
}