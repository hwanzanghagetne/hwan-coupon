package com.hwan.coupon.coupon.repository;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponIssueBatchRepository extends JpaRepository<CouponIssueBatch, Long> {

    @Query("SELECT b FROM CouponIssueBatch b WHERE b.status = :status AND b.requestedAt < :before")
    List<CouponIssueBatch> findByStatusAndRequestedAtBefore(
            @Param("status") BatchStatus status,
            @Param("before") LocalDateTime before
    );

    // PENDING 상태일 때만 PROCESSING으로 변경 — 반환값이 0이면 다른 인스턴스가 이미 선점한 것
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueBatch b SET b.status = :to WHERE b.id = :id AND b.status = :from")
    int updateStatusIfMatch(@Param("id") Long id, @Param("from") BatchStatus from, @Param("to") BatchStatus to);
}