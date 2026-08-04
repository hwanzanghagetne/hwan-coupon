package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.CouponIssueRequestStatus;
import com.hwan.coupon.coupon.repository.CouponIssueRequestRepository;
import com.hwan.coupon.coupon.service.CouponIssueWriter;
import com.hwan.coupon.coupon.service.CouponRedisService;

import com.hwan.coupon.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 선착순 발급 요청을 큐에서 하나씩 순차 소비해 DB에 반영하는 컨슈머.
 *
 * 당첨자 100명이 동시에 coupon/coupon_issue를 건드리며 생기던 데드락은,
 * 이 컨슈머가 메시지를 한 번에 하나씩만 처리하도록 만들어 동시 쓰기 자체를
 * 없애서 구조적으로 제거한다(관리자 대량발급의 BatchProcessor와 동일한 패턴).
 *
 * updateStatusIfMatch/markFailed는 커스텀 @Modifying 쿼리라, save()/findById()와
 * 달리 활성 트랜잭션이 없으면 TransactionRequiredException이 난다(BatchProcessor가
 * TransactionTemplate으로 감싸는 것과 같은 이유). saveIssue()는 자체 @Transactional이
 * 있어 별도로 감쌀 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeIssueProcessor {

    private final CouponIssueRequestRepository issueRequestRepository;
    private final CouponIssueWriter couponIssueWriter;
    private final CouponRedisService couponRedisService;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_FIRST_COME)
    public void processIssueRequest(FirstComeIssuePayload payload) {
        Long requestId = payload.requestId();

        int claimed = transactionTemplate.execute(status -> issueRequestRepository.updateStatusIfMatch(
                requestId, CouponIssueRequestStatus.PENDING, CouponIssueRequestStatus.PROCESSING, LocalDateTime.now()));
        if (claimed == 0) {
            log.warn("이미 처리(선점)된 발급 요청 무시 requestId={}", requestId);
            return;
        }

        try {
            couponIssueWriter.saveIssue(requestId, payload.couponId(), payload.userId(), payload.remaining());
            log.info("선착순 발급 처리 완료 requestId={} couponId={} userId={}",
                    requestId, payload.couponId(), payload.userId());
        } catch (DataIntegrityViolationException e) {
            // coupon_issue의 UNIQUE(user_id, coupon_id) 위반 — Redis 판정을 뚫고 들어온 중복
            couponRedisService.rollbackStockOnly(payload.couponId());
            transactionTemplate.executeWithoutResult(status ->
                    issueRequestRepository.markFailed(requestId, "이미 발급된 쿠폰입니다", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("선착순 발급 처리 실패 requestId={} error={}", requestId, e.getMessage(), e);
            couponRedisService.rollback(payload.couponId(), payload.userId());
            transactionTemplate.executeWithoutResult(status ->
                    issueRequestRepository.markFailed(requestId, "발급 처리 중 오류가 발생했습니다", LocalDateTime.now()));
        }
    }
}
