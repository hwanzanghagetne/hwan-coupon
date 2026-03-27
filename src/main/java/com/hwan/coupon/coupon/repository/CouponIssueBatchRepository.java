package com.hwan.coupon.coupon.repository;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;

import org.springframework.data.jpa.repository.JpaRepository;
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
}