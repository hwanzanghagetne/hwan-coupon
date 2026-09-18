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

    // 고착 여부 판단은 requestedAt(생성 시각)이 아니라 updatedAt(마지막 진행 시각)을 기준으로 한다.
    // PENDING 상태는 상태 전이 전까지 updatedAt이 requestedAt과 같게 유지되므로 이 기준이 그대로
    // 적용되고, PROCESSING 상태는 청크 처리마다 updatedAt이 갱신되므로 대용량 배치가 실제로
    // 진행 중이면 오탐하지 않는다. 이 쿼리는 "후보"만 찾고, 실제 FAILED 전환은
    // markFailedIfStale()이 조회 시점과 갱신 시점 사이의 경쟁을 막아준다.
    @Query("SELECT b FROM CouponIssueBatch b WHERE b.status = :status AND b.updatedAt < :before")
    List<CouponIssueBatch> findByStatusAndUpdatedAtBefore(
            @Param("status") BatchStatus status,
            @Param("before") LocalDateTime before
    );

    // PENDING 상태일 때만 PROCESSING으로 변경 — 반환값이 0이면 다른 인스턴스가 이미 선점한 것
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueBatch b SET b.status = :to, b.updatedAt = :now WHERE b.id = :id AND b.status = :from")
    int updateStatusIfMatch(@Param("id") Long id, @Param("from") BatchStatus from, @Param("to") BatchStatus to, @Param("now") LocalDateTime now);

    // 청크 하나 처리할 때마다 발급 건수를 누적하고 updatedAt을 갱신한다. "상태가 여전히
    // PROCESSING일 때만" 성공하도록 조건을 걸어서, 복구 스케줄러가 이미 FAILED로 확정한
    // 배치에 뒤늦게 도착한 청크가 계속 이어붙이는 걸 막는다. InnoDB는 이 UPDATE가 매칭되는
    // 순간 그 행에 락을 걸어 트랜잭션이 끝날 때까지 유지하므로, 이 UPDATE와 복구 스케줄러의
    // markFailedIfStale() 중 먼저 커밋되는 쪽이 자연스럽게 승자가 된다 — 진 쪽은 커밋된 적
    // 없는 잠정 데이터만 롤백하는 것이라 확정된 발급을 잃지 않는다.
    // 이 updatedAt 갱신은 BatchRecoveryScheduler 입장에서 "아직 살아서 진행 중"이라는
    // 하트비트 역할도 한다.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueBatch b SET b.issuedCount = b.issuedCount + :count, b.updatedAt = :now WHERE b.id = :id AND b.status = 'PROCESSING'")
    int incrementIssuedCountIfProcessing(@Param("id") Long id, @Param("count") int count, @Param("now") LocalDateTime now);

    // 마지막 청크의 DONE 전환도 같은 이유로 "여전히 PROCESSING일 때만" 성공하는 조건부
    // UPDATE로 처리한다. completedAt까지 이 한 번의 UPDATE에서 채운다(updateStatusIfMatch는
    // completedAt을 안 채우므로 재사용하지 않음).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponIssueBatch b SET b.status = 'DONE', b.completedAt = :now, b.updatedAt = :now WHERE b.id = :id AND b.status = 'PROCESSING'")
    int markDoneIfProcessing(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 후보로 조회된 시점 이후 실제로 진행됐을 수 있으므로(청크 커밋이 updatedAt을 갱신),
    // "상태가 여전히 fromStatus이고 updatedAt이 여전히 threshold보다 이전일 때만" FAILED로
    // 바꾸는 조건을 하나의 UPDATE 문에 담아 조회-후-판단 사이의 경쟁을 없앤다.
    // 반환값이 0이면 그 사이 진행이 있었다는 뜻이라 건드리지 않은 것이다.
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
}
