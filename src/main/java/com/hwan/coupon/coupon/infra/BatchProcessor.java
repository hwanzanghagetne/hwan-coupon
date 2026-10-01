package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;

import com.hwan.coupon.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
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

    @RabbitListener(queues = RabbitMQConfig.QUEUE, containerFactory = "adminBatchContainerFactory")
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
                        int doneUpdated = batchRepository.markDoneIfProcessing(batchId, now);
                        if (doneUpdated == 0) {
                            status.setRollbackOnly();
                            throw new IllegalStateException(
                                    "batchId=" + batchId + " 마지막 청크에서 DONE 전환 실패(예상치 못한 상태 변경)");
                        }
                    }
                    return chunkInserted;
                });

                actualInserted += inserted;
            }

            log.info("배치 처리 완료 batchId={} totalInserted={}", batchId, actualInserted);

        } catch (BatchNoLongerProcessingException e) {
            log.warn("[BatchProcessor] 배치 처리 중단 — 이미 다른 상태로 확정됨(복구 스케줄러 추정) batchId={}", batchId);
        } catch (Exception e) {
            log.error("배치 처리 실패 batchId={} error={}", batchId, e.getMessage(), e);
            // 이미 DONE/FAILED로 종료된 배치는 덮어쓰지 않는다.
            int failedUpdated = transactionTemplate.execute(status ->
                    batchRepository.markFailedIfNotFinished(batchId, LocalDateTime.now())
            );
            if (failedUpdated == 0) {
                log.warn("[BatchProcessor] batchId={}는 이미 다른 경로로 종료 확정되어 FAILED로 덮어쓰지 않음", batchId);
            }
            throw new AmqpRejectAndDontRequeueException("배치 처리 실패 batchId=" + batchId, e);
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
