package com.hwan.coupon.demo;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.domain.CouponIssueRequestStatus;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.dto.BatchIssueResponse;
import com.hwan.coupon.coupon.dto.CouponResponse;
import com.hwan.coupon.coupon.dto.CreateCouponRequest;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponIssueRequestRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.service.AdminBatchService;
import com.hwan.coupon.coupon.service.CouponRedisService;
import com.hwan.coupon.coupon.service.CouponService;
import com.hwan.coupon.demo.dto.BatchDemoRequest;
import com.hwan.coupon.demo.dto.BatchDemoResponse;
import com.hwan.coupon.demo.dto.BatchSummaryResponse;
import com.hwan.coupon.demo.dto.CouponSummaryResponse;
import com.hwan.coupon.demo.dto.FirstComeDemoRequest;
import com.hwan.coupon.demo.dto.FirstComeDemoResponse;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import com.hwan.coupon.member.Member;
import com.hwan.coupon.member.MemberRepository;
import com.hwan.coupon.member.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Service
@Profile("local")
@RequiredArgsConstructor
public class DemoScenarioService {

    private static final LocalDate DEMO_BIRTHDATE = LocalDate.of(1999, 1, 1);
    private static final String DEMO_PHONE = "010-0000-0000";
    private static final int BATCH_POLL_INTERVAL_MS = 200;
    private static final int BATCH_POLL_LIMIT = 50;
    private static final int FIRST_COME_POLL_INTERVAL_MS = 100;
    private static final int FIRST_COME_POLL_LIMIT = 100;

    private final CouponService couponService;
    private final AdminBatchService adminBatchService;
    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponIssueBatchRepository batchRepository;
    private final CouponIssueRequestRepository issueRequestRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final CouponRedisService couponRedisService;

    public FirstComeDemoResponse runFirstComeDemo(FirstComeDemoRequest request) {
        Coupon coupon = couponRepository.findById(request.couponId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        if (coupon.getIssueType() != IssueType.FIRST_COME) {
            throw new BusinessException(ErrorCode.COUPON_NOT_DIRECTLY_ISSUABLE);
        }
        if (coupon.getTotalQuantity() == null) {
            throw new BusinessException(ErrorCode.COUPON_FIRST_COME_REQUIRES_QUANTITY);
        }

        List<Long> userIds = createDemoUsers("fc", request.requestUserCount());
        int effectiveThreads = Math.min(request.threadCount(), request.requestUserCount());
        ExecutorService executor = Executors.newFixedThreadPool(effectiveThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userIds.size());
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger exhaustedCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        AtomicInteger otherFailureCount = new AtomicInteger();

        for (Long userId : userIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // 여기서 successCount는 "Redis 당첨 + 큐 접수 성공"을 의미한다.
                    // 실제 DB 반영은 FirstComeIssueProcessor가 비동기로 처리하므로,
                    // 최종 발급 결과는 아래 waitForFirstComeCompletion() 이후에 조회한다.
                    couponService.issueCoupon(coupon.getId(), userId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.COUPON_EXHAUSTED) {
                        exhaustedCount.incrementAndGet();
                    } else if (e.getErrorCode() == ErrorCode.COUPON_ALREADY_ISSUED) {
                        duplicateCount.incrementAndGet();
                    } else {
                        otherFailureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherFailureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long startedAt = System.nanoTime();
        startLatch.countDown();
        await(doneLatch);
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        waitForFirstComeCompletion(coupon.getId());

        Coupon savedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        long issuedRows = couponIssueRepository.countByCouponId(coupon.getId());

        return new FirstComeDemoResponse(
                coupon.getId(),
                request.requestUserCount(),
                coupon.getTotalQuantity(),
                effectiveThreads,
                successCount.get(),
                exhaustedCount.get(),
                duplicateCount.get(),
                otherFailureCount.get(),
                issuedRows,
                savedCoupon.getIssuedQuantity(),
                durationMs
        );
    }

    public BatchDemoResponse runBatchDemo(BatchDemoRequest request) {
        Coupon coupon = couponRepository.findById(request.couponId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        if (coupon.getIssueType() != IssueType.ADMIN_ISSUED) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_TYPE_MISMATCH);
        }

        List<Long> userIds = createDemoUsers("batch", request.userCount());
        long startedAt = System.nanoTime();
        BatchIssueResponse batch = adminBatchService.requestBatch(coupon.getId(), userIds);
        CouponIssueBatch latest = waitForBatch(batch.batchId());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Coupon savedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        long issuedRows = couponIssueRepository.countByCouponId(coupon.getId());
        boolean completed = latest.getStatus() == BatchStatus.DONE || latest.getStatus() == BatchStatus.FAILED;

        return new BatchDemoResponse(
                coupon.getId(),
                batch.batchId(),
                request.userCount(),
                latest.getStatus(),
                completed,
                issuedRows,
                savedCoupon.getIssuedQuantity(),
                durationMs
        );
    }

    public CouponSummaryResponse getCouponSummary(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        long issueCount = couponIssueRepository.countByCouponId(couponId);
        Long redisRemaining = couponRedisService.getRemainingStock(couponId);

        return new CouponSummaryResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getTotalQuantity(),
                coupon.getIssuedQuantity(),
                coupon.getStatus(),
                coupon.getIssueType(),
                issueCount,
                redisRemaining
        );
    }

    public BatchSummaryResponse getBatchSummary(Long batchId) {
        CouponIssueBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BATCH_NOT_FOUND));
        Coupon coupon = couponRepository.findById(batch.getCouponId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        long issueCount = couponIssueRepository.countByCouponId(batch.getCouponId());

        return new BatchSummaryResponse(
                batch.getId(),
                batch.getCouponId(),
                batch.getStatus(),
                batch.getTargetCount(),
                batch.getCompletedAt(),
                issueCount,
                coupon.getIssuedQuantity()
        );
    }

    private List<Long> createDemoUsers(String prefix, int count) {
        String encodedPassword = passwordEncoder.encode("123");
        String runId = prefix + "-" + System.currentTimeMillis();

        List<Member> members = IntStream.range(0, count)
                .mapToObj(i -> Member.create(
                        runId + "-" + i + "@test.com",
                        encodedPassword,
                        "demo-" + prefix + "-" + i,
                        DEMO_BIRTHDATE,
                        DEMO_PHONE,
                        Role.USER
                ))
                .toList();

        return memberRepository.saveAll(members).stream()
                .map(Member::getId)
                .toList();
    }

    private CouponIssueBatch waitForBatch(Long batchId) {
        CouponIssueBatch latest = batchRepository.findById(batchId).orElseThrow();

        for (int i = 0; i < BATCH_POLL_LIMIT; i++) {
            if (latest.getStatus() == BatchStatus.DONE || latest.getStatus() == BatchStatus.FAILED) {
                return latest;
            }
            try {
                Thread.sleep(BATCH_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return latest;
            }
            latest = batchRepository.findById(batchId).orElseThrow();
        }

        return latest;
    }

    // 큐에 접수된 선착순 발급 요청이 전부 SUCCESS/FAILED로 마무리될 때까지 대기.
    // FirstComeIssueProcessor가 순차 처리하므로, 접수 직후엔 아직 PENDING/PROCESSING일 수 있다.
    private void waitForFirstComeCompletion(Long couponId) {
        for (int i = 0; i < FIRST_COME_POLL_LIMIT; i++) {
            long inFlight = issueRequestRepository.countByCouponIdAndStatusIn(
                    couponId, List.of(CouponIssueRequestStatus.PENDING, CouponIssueRequestStatus.PROCESSING));
            if (inFlight == 0) {
                return;
            }
            try {
                Thread.sleep(FIRST_COME_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("데모 테스트가 중단되었습니다", e);
        }
    }
}



