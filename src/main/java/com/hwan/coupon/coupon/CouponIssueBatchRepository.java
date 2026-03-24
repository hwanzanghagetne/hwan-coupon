package com.hwan.coupon.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueBatchRepository extends JpaRepository<CouponIssueBatch, Long> {
}