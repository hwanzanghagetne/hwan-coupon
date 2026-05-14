package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;

import com.hwan.coupon.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
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
    private final JdbcTemplate jdbcTemplate;

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
                batchRepository.updateStatusIfMatch(batchId, BatchStatus.PENDING, BatchStatus.PROCESSING)
        );
        if (updated == 0) {
            log.warn("배치 선점 실패 batchId={}", batchId);
            return;
        }

        try {
            int actualInserted = 0;
            for (List<Long> chunk : partition(userIds, CHUNK_SIZE)) {
                actualInserted += bulkInsert(couponId, chunk);
            }
            log.info("bulk INSERT 완료 batchId={} totalInserted={}", batchId, actualInserted);

            final int count = actualInserted;
            transactionTemplate.executeWithoutResult(status ->
                    couponRepository.incrementIssuedQuantityBy(couponId, count)
            );

            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
                batch.markDone();
            });
            log.info("배치 처리 완료 batchId={}", batchId);

        } catch (Exception e) {
            log.error("배치 처리 실패 batchId={} error={}", batchId, e.getMessage(), e);
            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
                batch.markFailed();
            });
        }
    }

    private int bulkInsert(Long couponId, List<Long> userIds) {
        if (userIds.isEmpty()) return 0;

        // batchUpdate()는 rewriteBatchedStatements=true 환경에서 SUCCESS_NO_INFO(-2)를 반환할 수 있어
        // .sum()으로 집계하면 issuedQuantity가 잘못 계산됨.
        // 대신 청크 단위로 멀티 VALUES INSERT 문을 직접 조립하고 update()를 사용하면
        // MySQL이 실제 영향받은 행 수만 반환하므로 INSERT IGNORE 중복 스킵도 정확히 집계됨.
        StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO coupon_issue (coupon_id, user_id, status, issued_at) VALUES "
        );
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < userIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(?,?,'ISSUED',?)");
            params.add(couponId);
            params.add(userIds.get(i));
            params.add(now);
        }

        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
