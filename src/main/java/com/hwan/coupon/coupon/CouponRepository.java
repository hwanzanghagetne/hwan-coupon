package com.hwan.coupon.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1 WHERE c.id = :couponId")
    void incrementIssuedQuantity(@Param("couponId") Long couponId);

    @Modifying
    @Query("UPDATE Coupon c SET c.status = 'EXHAUSTED' WHERE c.id = :couponId")
    void markExhausted(@Param("couponId") Long couponId);

    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + :count WHERE c.id = :couponId")
    void incrementIssuedQuantityBy(@Param("couponId") Long couponId, @Param("count") int count);

    @Query("SELECT c.id FROM Coupon c WHERE c.status = 'ACTIVE' AND c.expiredAt < :now")
    List<Long> findExpiredActiveCouponIds(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Coupon c SET c.status = 'INACTIVE' WHERE c.id IN :ids")
    int markInactiveByIds(@Param("ids") List<Long> ids);

    @Modifying
    @Query("UPDATE Coupon c SET c.status = 'INACTIVE' WHERE c.id = :couponId AND c.status = 'ACTIVE'")
    int markInactive(@Param("couponId") Long couponId);
}
