package com.hwan.coupon.coupon.repository;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1, c.updatedAt = :now WHERE c.id = :couponId")
    void incrementIssuedQuantity(@Param("couponId") Long couponId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.status = :status, c.updatedAt = :now WHERE c.id = :couponId AND c.status = 'ACTIVE'")
    int markExhausted(@Param("couponId") Long couponId, @Param("status") CouponStatus status, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + :count, c.updatedAt = :now WHERE c.id = :couponId")
    void incrementIssuedQuantityBy(@Param("couponId") Long couponId, @Param("count") int count, @Param("now") LocalDateTime now);

    @Query("SELECT c.id FROM Coupon c WHERE c.status = :status AND c.expiredAt < :now")
    List<Long> findExpiredActiveCouponIds(@Param("status") CouponStatus status, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.status = :status, c.updatedAt = :now WHERE c.id IN :ids AND c.status = :currentStatus")
    int markInactiveByIds(@Param("ids") List<Long> ids, @Param("status") CouponStatus status, @Param("currentStatus") CouponStatus currentStatus, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.status = :status, c.updatedAt = :now WHERE c.id = :couponId AND c.status = :currentStatus")
    int markInactive(@Param("couponId") Long couponId, @Param("status") CouponStatus status, @Param("currentStatus") CouponStatus currentStatus, @Param("now") LocalDateTime now);
}
