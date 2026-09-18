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

    // PENDING: 정상 처리 시 수 초 내 PROCESSING으로 전환되므로 5분 이상이면 RabbitMQ 발행 실패로 판단.
    //   PENDING 상태는 상태 전이 전까지 updatedAt이 requestedAt과 같게 유지되므로 이 기준으로 충분함.
    // PROCESSING: updatedAt(마지막 진행 시각) 기준 10분. BatchProcessor가 청크 하나 처리할 때마다
    //   issuedCount와 함께 updatedAt을 갱신하므로, 대용량 배치가 실제로 계속 진행 중이면
    //   updatedAt이 계속 최신으로 유지돼 오탐하지 않는다. 진짜 멈춘 경우만 10분 뒤 감지된다.
    //
    // 이 복구는 "완전한 재처리 보장"이 아니라 영구 stuck 방지 + 운영 가시성 확보용 안전장치임.
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
            // 후보 조회 시점 이후 실제로 청크가 커밋돼 진행됐을 수 있다(그러면 updatedAt이 갱신됨).
            // "상태가 여전히 status이고 updatedAt이 여전히 threshold보다 이전일 때만" FAILED로
            // 바꾸는 조건을 하나의 UPDATE에 담아서, 조회-후-판단 사이의 경쟁을 없앤다.
            // 반환값이 0이면 그 사이 진행이 있었다는 뜻이라 건드리지 않은 것이다.
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
