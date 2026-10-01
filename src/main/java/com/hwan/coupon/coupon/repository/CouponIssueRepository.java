package com.hwan.coupon.coupon.repository;

import com.hwan.coupon.coupon.domain.CouponIssue;
import com.hwan.coupon.coupon.domain.CouponIssueStatus;

import com.hwan.coupon.coupon.dto.MonthlyCountProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

    Optional<CouponIssue> findByCouponIdAndUserId(Long couponId, Long userId);

    // 같은 (couponId, userId) 행에 대한 사용/복원 요청이 겹치는 것만 막는다 — 재고 경합과
    // 달리 한 쌍당 많아야 2~3개 요청이 겹치는 수준이라 비관적 락으로 충분하다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CouponIssue ci WHERE ci.couponId = :couponId AND ci.userId = :userId")
    Optional<CouponIssue> findByCouponIdAndUserIdForUpdate(@Param("couponId") Long couponId, @Param("userId") Long userId);

    long countByCouponId(Long couponId);

    @Query("SELECT ci.userId FROM CouponIssue ci WHERE ci.couponId = :couponId")
    List<Long> findUserIdsByCouponId(@Param("couponId") Long couponId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CouponIssue ci WHERE ci.couponId = :couponId")
    void deleteByCouponId(@Param("couponId") Long couponId);

    Page<CouponIssue> findAllByUserId(Long userId, Pageable pageable);

    @Query(value = """
            SELECT DATE_FORMAT(issued_at, '%Y-%m') AS month, COUNT(*) AS count
            FROM coupon_issue
            WHERE issued_at >= :start AND issued_at < :end
            GROUP BY DATE_FORMAT(issued_at, '%Y-%m')
            """, nativeQuery = true)
    List<MonthlyCountProjection> countIssuedByMonth(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT DATE_FORMAT(used_at, '%Y-%m') AS month, COUNT(*) AS count
            FROM coupon_issue
            WHERE used_at >= :start AND used_at < :end
            GROUP BY DATE_FORMAT(used_at, '%Y-%m')
            """, nativeQuery = true)
    List<MonthlyCountProjection> countUsedByMonth(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssue ci SET ci.status = :toStatus WHERE ci.couponId IN :couponIds AND ci.status = :fromStatus")
    int expireIssuedByCouponIds(@Param("couponIds") List<Long> couponIds,
                                @Param("toStatus") CouponIssueStatus toStatus,
                                @Param("fromStatus") CouponIssueStatus fromStatus);
}