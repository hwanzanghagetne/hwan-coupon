package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.dto.MonthlyStatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

    Optional<CouponIssue> findByCouponIdAndUserId(Long couponId, Long userId);

    long countByCouponId(Long couponId);

    @Modifying
    @Query("DELETE FROM CouponIssue ci WHERE ci.couponId = :couponId")
    void deleteByCouponId(@Param("couponId") Long couponId);

    List<CouponIssue> findAllByUserId(Long userId);

    @Query(value = """
            SELECT DATE_FORMAT(issued_at, '%Y-%m')                             AS month,
                   COUNT(*)                                                     AS totalIssued,
                   COALESCE(SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END), 0) AS totalUsed
            FROM coupon_issue
            WHERE YEAR(issued_at) = :year
            GROUP BY DATE_FORMAT(issued_at, '%Y-%m')
            ORDER BY DATE_FORMAT(issued_at, '%Y-%m')
            """, nativeQuery = true)
    List<MonthlyStatsProjection> findMonthlyStatsByYear(@Param("year") int year);
}