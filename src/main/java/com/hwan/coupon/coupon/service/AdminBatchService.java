package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import com.hwan.coupon.coupon.infra.BatchMessagePayload;

import com.hwan.coupon.coupon.dto.BatchIssueResponse;
import com.hwan.coupon.global.config.RabbitMQConfig;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBatchService {

    private final CouponRepository couponRepository;
    private final CouponIssueBatchRepository batchRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    public BatchIssueResponse requestBatch(Long couponId, List<Long> userIds) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        if (coupon.getIssueType() != IssueType.ADMIN_ISSUED) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_TYPE_MISMATCH);
        }

        List<Long> uniqueUserIds = userIds.stream().distinct().toList();

        CouponIssueBatch saved = transactionTemplate.execute(status -> {
            CouponIssueBatch batch = CouponIssueBatch.create(couponId, uniqueUserIds.size());
            return batchRepository.save(batch);
        });

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                new BatchMessagePayload(saved.getId(), couponId, uniqueUserIds)
        );
        log.info("배치 메시지 발행 batchId={} couponId={} targetCount={}", saved.getId(), couponId, userIds.size());

        return BatchIssueResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BatchIssueResponse getBatchStatus(Long batchId) {
        CouponIssueBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BATCH_NOT_FOUND));
        return BatchIssueResponse.from(batch);
    }
}