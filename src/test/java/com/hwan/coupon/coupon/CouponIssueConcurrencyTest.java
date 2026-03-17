package com.hwan.coupon.coupon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    private Long couponId;

    @BeforeEach
    void setUp() {
        Coupon coupon = Coupon.create(
                "동시성테스트쿠폰",
                DiscountType.FIXED,
                1000,
                500,
                null,
                IssueType.FIRST_COME,
                null,
                null,
                LocalDateTime.now().plusDays(30)
        );
        couponId = couponRepository.save(coupon).getId();
    }

    @AfterEach
    void tearDown() {
        couponIssueRepository.deleteAll();
        couponRepository.deleteAll();
    }

    @Test
    void 동시에_100명_발급요청() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executor.submit(() -> {
                try {
                    couponService.issueCoupon(couponId, userId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Coupon coupon = couponRepository.findById(couponId).orElseThrow();
        long issueCount = couponIssueRepository.count();

        System.out.println("실제 issued_quantity: " + coupon.getIssuedQuantity());
        System.out.println("실제 coupon_issue 수: " + issueCount);

        assertThat(coupon.getIssuedQuantity()).isEqualTo(100);
        assertThat(issueCount).isEqualTo(100);
    }
}