package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.domain.CouponStatus;
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
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBatchService {

    private final CouponRepository couponRepository;
    private final CouponIssueBatchRepository batchRepository;
    private final RabbitTemplate rabbitTemplate;

    public BatchIssueResponse requestBatch(Long couponId, List<Long> userIds) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        if (coupon.getIssueType() != IssueType.ADMIN_ISSUED) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_TYPE_MISMATCH);
        }
        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.COUPON_NOT_ACTIVE);
        }
        if (LocalDateTime.now().isAfter(coupon.getExpiredAt())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        List<Long> uniqueUserIds = userIds.stream().distinct().toList();

        CouponIssueBatch batch = CouponIssueBatch.create(couponId, uniqueUserIds.size());
        CouponIssueBatch saved = batchRepository.save(batch);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROUTING_KEY,
                    new BatchMessagePayload(saved.getId(), couponId, uniqueUserIds)
            );
            log.info("배치 메시지 발행 batchId={} couponId={} targetCount={}", saved.getId(), couponId, saved.getTargetCount());
        } catch (AmqpException e) {
            log.error("[PublishUncertain] 배치 발행 결과 불확실 batchId={} couponId={} error={}",
                    saved.getId(), couponId, e.getMessage(), e);
        }

        return BatchIssueResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BatchIssueResponse getBatchStatus(Long batchId) {
        CouponIssueBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BATCH_NOT_FOUND));
        return BatchIssueResponse.from(batch);
    }
}
