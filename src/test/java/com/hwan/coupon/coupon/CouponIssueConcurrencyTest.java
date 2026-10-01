package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.service.CouponRedisService;
import com.hwan.coupon.coupon.service.CouponService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import org.springframework.test.context.ActiveProfiles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class CouponIssueConcurrencyTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("coupon")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> mysql.getJdbcUrl() + "?rewriteBatchedStatements=true&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private CouponRedisService couponRedisService;

    private Long couponId;
    private static final int THREAD_COUNT = 100;
    private static final int TOTAL_QUANTITY = 50;

    @BeforeEach
    void setUp() {
        Coupon coupon = Coupon.create(
                "동시성테스트쿠폰",
                DiscountType.FIXED,
                1000,
                TOTAL_QUANTITY,
                null,
                IssueType.FIRST_COME,
                null,
                null,
                LocalDateTime.now().plusDays(30)
        );
        couponId = couponRepository.save(coupon).getId();

        // CouponService.createCoupon()을 거치지 않고 리포지토리로 직접 생성하므로,
        // 그 경로에서 자동으로 되던 Redis 재고 초기화를 여기서 직접 해줘야 한다.
        // issueCoupon()은 이제 재고 키가 없으면 자동 복구하지 않고 거절하므로 필수.
        couponRedisService.initStock(couponId, TOTAL_QUANTITY);

        // coupon_issue가 member(id)를 참조하는 FK가 걸려있어서,
        // 실제 member row가 없으면 발급이 FK 위반으로 실패한다.
        createMembers(THREAD_COUNT);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM coupon_issue WHERE coupon_id = ?", couponId);
        couponRepository.deleteById(couponId);
        jdbcTemplate.update("DELETE FROM member WHERE id BETWEEN 1 AND ?", THREAD_COUNT);
    }

    private void createMembers(int count) {
        StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO member (id, email, password, name, birthdate, phone, role, created_at, updated_at) VALUES "
        );
        java.util.List<Object> params = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            if (i > 1) sql.append(",");
            sql.append("(?,?,?,?,?,?,?,NOW(),NOW())");
            params.add((long) i);
            params.add("concurrency-test" + i + "@test.com");
            params.add("password");
            params.add("동시성테스트유저" + i);
            params.add(java.time.LocalDate.of(1990, 1, 1));
            params.add("010-0000-0000");
            params.add("USER");
        }
        jdbcTemplate.update(sql.toString(), params.toArray());
    }

    @Test
    @DisplayName("재고(50)보다 많은 100명이 동시에 요청해도 정확히 50건만 발급된다")
    void 동시에_100명_발급요청_재고초과_방지() throws InterruptedException {
        int totalQuantity = TOTAL_QUANTITY;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
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

        // 선착순 발급은 이제 Redis 당첨 즉시 응답하고 실제 DB 반영은 큐 컨슈머가 처리하므로,
        // 컨슈머가 처리를 끝낼 때까지 기다린 뒤에 최종 상태를 확인해야 한다.
        await().atMost(10, TimeUnit.SECONDS).until(() ->
                couponRepository.findById(couponId).orElseThrow().getIssuedQuantity() == totalQuantity
        );

        Coupon coupon = couponRepository.findById(couponId).orElseThrow();
        long issueCount = couponIssueRepository.countByCouponId(couponId);

        assertThat(coupon.getIssuedQuantity()).isEqualTo(totalQuantity);
        assertThat(issueCount).isEqualTo(totalQuantity);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 여러 번 요청해도 한 장만 발급된다")
    void 같은사용자_동시중복요청_한장만발급() throws InterruptedException {
        int requestCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        AtomicInteger accepted = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    couponService.issueCoupon(couponId, 1L);
                    accepted.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        await().atMost(10, TimeUnit.SECONDS).until(() ->
                couponIssueRepository.countByCouponId(couponId) == 1
        );

        assertThat(accepted).hasValue(1);
        assertThat(couponIssueRepository.countByCouponId(couponId)).isEqualTo(1);
        assertThat(couponRepository.findById(couponId).orElseThrow().getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 발급 건에 사용 요청이 동시에 들어와도 정확히 한 번만 성공한다")
    void 같은발급건_동시사용요청_한번만성공() throws InterruptedException {
        Long userId = 1L;
        jdbcTemplate.update(
                "INSERT INTO coupon_issue (coupon_id, user_id, status, issued_at) VALUES (?, ?, 'ISSUED', NOW())",
                couponId, userId);

        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    couponService.useCoupon(couponId, userId, 10_000);
                    succeeded.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        assertThat(succeeded).hasValue(1);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM coupon_issue WHERE coupon_id = ? AND user_id = ?",
                String.class, couponId, userId);
        assertThat(status).isEqualTo("USED");
    }
}
