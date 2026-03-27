package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchRecoveryScheduler {

    private final CouponIssueBatchRepository batchRepository;
    private final TransactionTemplate transactionTemplate;

    // RabbitMQ 발행 실패로 PENDING에 고착된 배치를 FAILED로 마킹
    // 정상 처리는 수 초 내 PROCESSING으로 전환되므로 5분 이상 PENDING은 발행 실패로 판단
    @Scheduled(fixedDelay = 60_000)
    public void recoverStuckBatches() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<CouponIssueBatch> stuckBatches = batchRepository.findByStatusAndRequestedAtBefore(
                BatchStatus.PENDING, threshold
        );

        if (stuckBatches.isEmpty()) {
            return;
        }

        log.warn("[BatchRecoveryScheduler] PENDING 고착 배치 {}건 발견 — FAILED 처리", stuckBatches.size());

        for (CouponIssueBatch batch : stuckBatches) {
            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch fresh = batchRepository.findById(batch.getId()).orElseThrow();
                // 다른 스레드가 이미 처리했을 수 있으므로 상태 재확인
                if (fresh.getStatus() == BatchStatus.PENDING) {
                    fresh.markFailed();
                    log.warn("[BatchRecoveryScheduler] batchId={} FAILED 처리 (발행 실패 추정)", fresh.getId());
                }
            });
        }
    }
}