package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.dto.BatchIssueResponse;
import com.hwan.coupon.global.config.RabbitMQConfig;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminBatchService {

    private final CouponRepository couponRepository;
    private final CouponIssueBatchRepository batchRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    public BatchIssueResponse requestBatch(Long couponId, List<Long> userIds) {
        couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // save만 트랜잭션으로 감싸서 즉시 커밋 → 커밋 완료 후 메시지 발행
        CouponIssueBatch saved = transactionTemplate.execute(status -> {
            CouponIssueBatch batch = CouponIssueBatch.create(couponId, userIds.size());
            return batchRepository.save(batch);
        });

        if (saved == null) throw new BusinessException(ErrorCode.COUPON_CONFLICT);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                new BatchMessagePayload(saved.getId(), couponId, userIds)
        );

        return BatchIssueResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BatchIssueResponse getBatchStatus(Long batchId) {
        CouponIssueBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BATCH_NOT_FOUND));
        return BatchIssueResponse.from(batch);
    }
}