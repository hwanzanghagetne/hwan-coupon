package com.hwan.coupon.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BatchProcessor {

    private final CouponIssueBatchRepository batchRepository;
    private final CouponRepository couponRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    private static final int CHUNK_SIZE = 1000;

    @Async("batchExecutor")
    public void processBatch(Long batchId, Long couponId, List<Long> userIds) {
        // 1단계: PROCESSING — 독립 트랜잭션으로 커밋
        transactionTemplate.executeWithoutResult(status -> {
            CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
            batch.markProcessing();
        });

        try {
            // 2단계: 1000건씩 청크로 나눠 bulk INSERT
            // rewriteBatchedStatements=true 로 MySQL이 multi-value INSERT로 재작성
            // e.g. INSERT INTO coupon_issue VALUES (1,...), (2,...), ... — DB 왕복 1회/청크
            List<List<Long>> chunks = partition(userIds, CHUNK_SIZE);
            for (List<Long> chunk : chunks) {
                bulkInsert(couponId, chunk);
            }

            // issued_quantity 한 번에 N만큼 업데이트
            transactionTemplate.executeWithoutResult(status ->
                    couponRepository.incrementIssuedQuantityBy(couponId, userIds.size())
            );

            // 3단계: DONE — 독립 트랜잭션으로 커밋
            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
                batch.markDone();
            });

        } catch (Exception e) {
            // INSERT 실패 시 FAILED — 별도 트랜잭션이므로 롤백 영향 없음
            transactionTemplate.executeWithoutResult(status -> {
                CouponIssueBatch batch = batchRepository.findById(batchId).orElseThrow();
                batch.markFailed();
            });
        }
    }

    private void bulkInsert(Long couponId, List<Long> userIds) {
        String sql = "INSERT INTO coupon_issue (coupon_id, user_id, status, issued_at, quantity_synced) " +
                     "VALUES (?, ?, 'ISSUED', ?, true)";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        jdbcTemplate.batchUpdate(sql, userIds, userIds.size(), (ps, userId) -> {
            ps.setLong(1, couponId);
            ps.setLong(2, userId);
            ps.setTimestamp(3, now);
        });
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
