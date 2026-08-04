package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
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

    private Long couponId;
    private static final int THREAD_COUNT = 100;

    @BeforeEach
    void setUp() {
        Coupon coupon = Coupon.create(
                "동시성테스트쿠폰",
                DiscountType.FIXED,
                1000,
                50,
                null,
                IssueType.FIRST_COME,
                null,
                null,
                LocalDateTime.now().plusDays(30)
        );
        couponId = couponRepository.save(coupon).getId();

        // coupon_issue / coupon_issue_request가 member(id)를 참조하는 FK가 걸려있어서,
        // 실제 member row가 없으면 발급이 FK 위반으로 실패한다.
        createMembers(THREAD_COUNT);
    }

    @AfterEach
    void tearDown() {
        couponIssueRepository.deleteByCouponId(couponId);
        jdbcTemplate.update("DELETE FROM coupon_issue_request WHERE coupon_id = ?", couponId);
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
        int totalQuantity = 50;
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
}
