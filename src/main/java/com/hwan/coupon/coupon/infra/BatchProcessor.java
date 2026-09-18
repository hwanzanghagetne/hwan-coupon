package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;

import com.hwan.coupon.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchProcessor {

    private final CouponIssueBatchRepository batchRepository;
    private final CouponRepository couponRepository;
    private final TransactionTemplate transactionTemplate;
    private final CouponIssueBulkInsertSupport bulkInsertSupport;

    private static final int CHUNK_SIZE = 1000;

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void processBatch(BatchMessagePayload payload) {
        Long batchId  = payload.batchId();
        Long couponId = payload.couponId();
        List<Long> userIds = payload.userIds();

        CouponIssueBatch existing = batchRepository.findById(batchId).orElseThrow();
        if (existing.getStatus() == BatchStatus.DONE || existing.getStatus() == BatchStatus.FAILED) {
            log.warn("이미 처리된 배치 메시지 무시 batchId={} status={}", batchId, existing.getStatus());
            return;
        }

        log.info("배치 처리 시작 batchId={} couponId={} targetCount={}", batchId, couponId, userIds.size());

        int updated = transactionTemplate.execute(status ->
                batchRepository.updateStatusIfMatch(batchId, BatchStatus.PENDING, BatchStatus.PROCESSING, LocalDateTime.now())
        );
        if (updated == 0) {
            log.warn("배치 선점 실패 batchId={}", batchId);
            return;
        }

        try {
            List<List<Long>> chunks = partition(userIds, CHUNK_SIZE);
            int actualInserted = 0;

            for (int i = 0; i < chunks.size(); i++) {
                List<Long> chunk = chunks.get(i);
                boolean isLastChunk = (i == chunks.size() - 1);

                // 이 청크의 INSERT, issuedQuantity 반영, 발급 건수 갱신(+ 마지막 청크면 DONE 마킹)을
                // 하나의 트랜잭션으로 묶는다. incrementIssuedCountIfProcessing()이 "여전히
                // PROCESSING일 때만" 성공하므로, 그 사이 BatchRecoveryScheduler가 이 배치를
                // 먼저 FAILED로 확정했다면 0건이 반환된다 — 이 트랜잭션은 커밋된 적 없으므로
                // 롤백해도 이미 확정된 발급을 잃지 않는다.
                int inserted = transactionTemplate.execute(status -> {
                    int chunkInserted = bulkInsertSupport.insertIgnore(couponId, chunk);
                    LocalDateTime now = LocalDateTime.now();
                    couponRepository.incrementIssuedQuantityBy(couponId, chunkInserted, now);

                    int countUpdated = batchRepository.incrementIssuedCountIfProcessing(batchId, chunkInserted, now);
                    if (countUpdated == 0) {
                        status.setRollbackOnly();
                        throw new BatchNoLongerProcessingException(batchId);
                    }

                    if (isLastChunk) {
                        // 위 조건부 UPDATE가 이미 이 트랜잭션 안에서 PROCESSING을 확인하고
                        // 행 락을 잡은 상태라, 같은 트랜잭션의 이 UPDATE는 항상 매칭된다.
                        // 그래도 향후 변경에 대비해 결과를 확인하고 로그로 남긴다.
                        int doneUpdated = batchRepository.markDoneIfProcessing(batchId, now);
                        if (doneUpdated == 0) {
                            log.warn("[BatchProcessor] 마지막 청크에서 DONE 전환 실패(예상치 못한 상태 변경) batchId={}", batchId);
                        }
                    }
                    return chunkInserted;
                });

                actualInserted += inserted;
            }

            log.info("배치 처리 완료 batchId={} totalInserted={}", batchId, actualInserted);

        } catch (BatchNoLongerProcessingException e) {
            // 복구 스케줄러와의 경쟁에서 진 것 — 이미 다른 쪽이 상태를 확정했으므로 그 판정을
            // 덮어쓰지 않는다(아래 catch처럼 markFailed()를 다시 호출하면 completedAt 등이
            // 갱신되어, 복구 스케줄러가 남긴 최초 판정 시각이 지워진다).
            log.warn("[BatchProcessor] 배치 처리 중단 — 이미 다른 상태로 확정됨(복구 스케줄러 추정) batchId={}", batchId);
        } catch (Exception e) {
            log.error("배치 처리 실패 batchId={} error={}", batchId, e.getMessage(), e);
            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
                batch.markFailed();
            });
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private static final class BatchNoLongerProcessingException extends RuntimeException {
        BatchNoLongerProcessingException(Long batchId) {
            super("batchId=" + batchId + "가 더 이상 PROCESSING 상태가 아닙니다(복구 스케줄러에 의해 이미 처리된 것으로 추정)");
        }
    }
}
