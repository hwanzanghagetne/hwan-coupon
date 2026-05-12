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

    // PENDING: 정상 처리 시 수 초 내 PROCESSING으로 전환되므로 5분 이상이면 RabbitMQ 발행 실패로 판단
    // PROCESSING: 대용량 배치(1,000건)도 수십 초 내 완료되므로 10분 이상이면 처리 중 프로세스 비정상 종료로 판단
    //
    // [한계] 두 경우 모두 requestedAt(배치 생성 시각) 기준으로 timeout을 판단함.
    // 이상적으로는 상태 전환 시각(updatedAt)을 별도 컬럼으로 관리해야 더 정확하지만,
    // 현재 모델에서는 requestedAt이 유일한 기준임.
    // 이 복구는 "완전한 재처리 보장"이 아니라 영구 stuck 방지 + 운영 가시성 확보용 안전장치임.
    @Scheduled(fixedDelay = 60_000)
    public void recoverStuckBatches() {
        recoverStuckPending();
        recoverStuckProcessing();
    }

    private void recoverStuckPending() {
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
                if (fresh.getStatus() == BatchStatus.PENDING) {
                    fresh.markFailed();
                    log.warn("[BatchRecoveryScheduler] PENDING→FAILED batchId={} couponId={} requestedAt={} (RabbitMQ 발행 실패 추정)",
                            fresh.getId(), fresh.getCouponId(), fresh.getRequestedAt());
                }
            });
        }
    }

    private void recoverStuckProcessing() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<CouponIssueBatch> stuckBatches = batchRepository.findByStatusAndRequestedAtBefore(
                BatchStatus.PROCESSING, threshold
        );

        if (stuckBatches.isEmpty()) {
            return;
        }

        log.warn("[BatchRecoveryScheduler] PROCESSING 고착 배치 {}건 발견 — FAILED 처리", stuckBatches.size());

        for (CouponIssueBatch batch : stuckBatches) {
            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch fresh = batchRepository.findById(batch.getId()).orElseThrow();
                if (fresh.getStatus() == BatchStatus.PROCESSING) {
                    fresh.markFailed();
                    log.warn("[BatchRecoveryScheduler] PROCESSING→FAILED batchId={} couponId={} requestedAt={} (처리 중 프로세스 비정상 종료 추정)",
                            fresh.getId(), fresh.getCouponId(), fresh.getRequestedAt());
                }
            });
        }
    }
}