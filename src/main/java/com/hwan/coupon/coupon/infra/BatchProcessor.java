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
import java.util.Arrays;
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

        // 멱등성 체크 — 메시지 재전달 시 이미 완료/실패된 배치는 무시
        // RabbitMQ는 ACK 전 크래시 시 같은 메시지를 재전달하므로 중복 처리 방지 필요
        CouponIssueBatch existing = batchRepository.findById(batchId).orElseThrow();
        if (existing.getStatus() == BatchStatus.DONE || existing.getStatus() == BatchStatus.FAILED) {
            log.warn("이미 처리된 배치 메시지 무시 batchId={} status={}", batchId, existing.getStatus());
            return;
        }

        log.info("배치 처리 시작 batchId={} couponId={} targetCount={}", batchId, couponId, userIds.size());

        // 1단계: PROCESSING — 독립 트랜잭션으로 커밋
        transactionTemplate.executeWithoutResult(status -> {
            CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
            batch.markProcessing();
        });

        try {
            // 2단계: 1000건씩 청크로 나눠 bulk INSERT
            int actualInserted = 0;
            for (List<Long> chunk : partition(userIds, CHUNK_SIZE)) {
                actualInserted += bulkInsert(couponId, chunk);
            }
            log.info("bulk INSERT 완료 batchId={} totalInserted={}", batchId, actualInserted);

            // issued_quantity를 실제 INSERT된 건수만큼만 업데이트
            final int count = actualInserted;
            transactionTemplate.executeWithoutResult(status ->
                    couponRepository.incrementIssuedQuantityBy(couponId, count)
            );

            // 3단계: DONE — 독립 트랜잭션으로 커밋
            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
                batch.markDone();
            });
            log.info("배치 처리 완료 batchId={}", batchId);

        } catch (Exception e) {
            log.error("배치 처리 실패 batchId={} error={}", batchId, e.getMessage(), e);
            // INSERT 실패 시 FAILED — 별도 트랜잭션이므로 롤백 영향 없음
            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
                batch.markFailed();
            });
        }
    }

    private int bulkInsert(Long couponId, List<Long> userIds) {
        String sql = "INSERT IGNORE INTO coupon_issue (coupon_id, user_id, status, issued_at, quantity_synced) " +
                     "VALUES (?, ?, 'ISSUED', ?, true)";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        int[][] results = jdbcTemplate.batchUpdate(sql, userIds, userIds.size(), (ps, userId) -> {
            ps.setLong(1, couponId);
            ps.setLong(2, userId);
            ps.setTimestamp(3, now);
        });
        return Arrays.stream(results).flatMapToInt(Arrays::stream).sum();
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
