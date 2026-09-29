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

    // 고착 후보 조회. 실제 FAILED 확정은 markFailedIfStale()의 조건부 UPDATE가 담당한다.
    @Query("SELECT b FROM CouponIssueBatch b WHERE b.status = :status AND b.updatedAt < :before")
    List<CouponIssueBatch> findByStatusAndUpdatedAtBefore(
            @Param("status") BatchStatus status,
            @Param("before") LocalDateTime before
    );

    // PENDING 상태일 때만 PROCESSING으로 변경 — 반환값이 0이면 다른 인스턴스가 이미 선점한 것
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueBatch b SET b.status = :to, b.updatedAt = :now WHERE b.id = :id AND b.status = :from")
    int updateStatusIfMatch(@Param("id") Long id, @Param("from") BatchStatus from, @Param("to") BatchStatus to, @Param("now") LocalDateTime now);

    // PROCESSING 상태일 때만 성공. updatedAt 갱신은 하트비트 역할도 겸한다.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueBatch b SET b.issuedCount = b.issuedCount + :count, b.updatedAt = :now WHERE b.id = :id AND b.status = 'PROCESSING'")
    int incrementIssuedCountIfProcessing(@Param("id") Long id, @Param("count") int count, @Param("now") LocalDateTime now);

    // 마지막 청크의 DONE 전환도 PROCESSING 조건부 UPDATE로 처리(completedAt까지 함께 채움).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueBatch b SET b.status = 'DONE', b.completedAt = :now, b.updatedAt = :now WHERE b.id = :id AND b.status = 'PROCESSING'")
    int markDoneIfProcessing(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 조회-후-판단 사이의 경쟁을 조건부 UPDATE 하나로 없앤다. 0건이면 그 사이 진행이
    // 있었다는 뜻이라 건드리지 않은 것.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CouponIssueBatch b
               SET b.status = 'FAILED', b.completedAt = :now, b.updatedAt = :now
             WHERE b.id = :id AND b.status = :fromStatus AND b.updatedAt < :threshold
            """)
    int markFailedIfStale(@Param("id") Long id,
                           @Param("fromStatus") BatchStatus fromStatus,
                           @Param("threshold") LocalDateTime threshold,
                           @Param("now") LocalDateTime now);

    // BatchProcessor의 일반 예외 처리 전용. DONE/FAILED로 이미 종료된 배치는 건드리지 않는다.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CouponIssueBatch b
               SET b.status = 'FAILED', b.completedAt = :now, b.updatedAt = :now
             WHERE b.id = :id AND b.status NOT IN (com.hwan.coupon.coupon.domain.BatchStatus.DONE, com.hwan.coupon.coupon.domain.BatchStatus.FAILED)
            """)
    int markFailedIfNotFinished(@Param("id") Long id, @Param("now") LocalDateTime now);
}
