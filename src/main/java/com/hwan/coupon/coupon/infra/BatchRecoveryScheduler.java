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

    // PENDING 5분 / PROCESSING 10분(updatedAt 기준) 고착 시 FAILED 처리.
    // 완전한 재처리 보장이 아니라 영구 stuck 방지용 안전장치.
    @Scheduled(fixedDelay = 60_000)
    public void recoverStuckBatches() {
        recoverStuckByStatus(BatchStatus.PENDING, 5, "RabbitMQ 발행 실패 추정");
        recoverStuckByStatus(BatchStatus.PROCESSING, 10, "처리 중 프로세스 비정상 종료 추정");
    }

    private void recoverStuckByStatus(BatchStatus status, int timeoutMinutes, String reasonGuess) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<CouponIssueBatch> candidates = batchRepository.findByStatusAndUpdatedAtBefore(status, threshold);

        if (candidates.isEmpty()) {
            return;
        }

        log.warn("[BatchRecoveryScheduler] {} 고착 후보 {}건 발견", status, candidates.size());

        for (CouponIssueBatch candidate : candidates) {
            int updated = transactionTemplate.execute(txStatus ->
                    batchRepository.markFailedIfStale(candidate.getId(), status, threshold, LocalDateTime.now())
            );

            if (updated > 0) {
                log.warn("[BatchRecoveryScheduler] {}→FAILED batchId={} couponId={} issuedCount={}/{} ({})",
                        status, candidate.getId(), candidate.getCouponId(),
                        candidate.getIssuedCount(), candidate.getTargetCount(), reasonGuess);
            } else {
                log.info("[BatchRecoveryScheduler] batchId={}는 조회 이후 이미 진행되어 건드리지 않음", candidate.getId());
            }
        }
    }
}
