package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.dto.CouponCacheDto;
import com.hwan.coupon.coupon.service.CouponCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CouponIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("coupon").withUsername("test").withPassword("test");

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

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CouponCacheService couponCacheService;

    // 기본 RestTemplate은 HttpURLConnection 기반이라 PATCH를 못 보내므로 JDK HttpClient 팩토리를 쓴다
    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());
    private String adminCookie;

    @BeforeEach
    void setUp() {
        String base = "http://localhost:" + port;
        String email = "coupon-admin-" + System.nanoTime() + "@test.com";
        Map<String, Object> signupBody = Map.of(
                "email", email, "password", "password123", "name", "admin",
                "birthdate", "1990-01-01", "phone", "010-1234-5678"
        );
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(base + "/api/members/signup", new HttpEntity<>(signupBody, jsonHeaders), String.class);
        jdbcTemplate.update("UPDATE member SET role='ADMIN' WHERE email=?", email);

        Map<String, String> loginBody = Map.of("email", email, "password", "password123");
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                base + "/api/members/login", new HttpEntity<>(loginBody, jsonHeaders), String.class);
        adminCookie = loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, adminCookie);
        return headers;
    }

    private Long createCoupon(String name) {
        Map<String, Object> body = Map.of(
                "name", name, "discountType", "FIXED", "discountValue", 1000,
                "issueType", "ADMIN_ISSUED", "expiredAt", "2099-01-01T00:00:00"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/coupons", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), Map.class);
        return Long.valueOf(response.getBody().get("id").toString());
    }

    @Test
    void 이름이_255자를_넘으면_400을_받는다() {
        String base = "http://localhost:" + port;
        Map<String, Object> body = Map.of(
                "name", "a".repeat(256), "discountType", "FIXED", "discountValue", 1000,
                "issueType", "ADMIN_ISSUED", "expiredAt", "2099-01-01T00:00:00"
        );

        try {
            restTemplate.exchange(base + "/api/coupons", HttpMethod.POST, new HttpEntity<>(body, adminHeaders()), String.class);
            fail("256자 이름으로 생성했는데 예외가 발생하지 않았습니다");
        } catch (HttpStatusCodeException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void ACTIVE_쿠폰_비활성화는_204이고_DB_상태는_INACTIVE로_바뀐다() {
        String base = "http://localhost:" + port;
        Long couponId = createCoupon("비활성화대상");

        ResponseEntity<Void> response = restTemplate.exchange(
                base + "/api/coupons/" + couponId + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders()), Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        String status = jdbcTemplate.queryForObject("SELECT status FROM coupon WHERE id = ?", String.class, couponId);
        assertThat(status).isEqualTo("INACTIVE");
    }

    @Test
    void 이미_INACTIVE인_쿠폰을_다시_비활성화해도_204를_받는다() {
        String base = "http://localhost:" + port;
        Long couponId = createCoupon("재호출대상");
        restTemplate.exchange(base + "/api/coupons/" + couponId + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders()), Void.class);

        ResponseEntity<Void> response = restTemplate.exchange(
                base + "/api/coupons/" + couponId + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders()), Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void EXHAUSTED_쿠폰을_비활성화하면_204이고_상태는_EXHAUSTED로_유지된다() {
        String base = "http://localhost:" + port;
        Long couponId = createCoupon("소진대상");
        jdbcTemplate.update("UPDATE coupon SET status='EXHAUSTED' WHERE id=?", couponId);

        ResponseEntity<Void> response = restTemplate.exchange(
                base + "/api/coupons/" + couponId + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders()), Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        String status = jdbcTemplate.queryForObject("SELECT status FROM coupon WHERE id = ?", String.class, couponId);
        assertThat(status).isEqualTo("EXHAUSTED");
    }

    @Test
    void 존재하지_않는_쿠폰_비활성화는_404를_받는다() {
        String base = "http://localhost:" + port;

        try {
            restTemplate.exchange(base + "/api/coupons/999999/deactivate", HttpMethod.PATCH,
                    new HttpEntity<>(adminHeaders()), Void.class);
            fail("존재하지 않는 쿠폰인데 예외가 발생하지 않았습니다");
        } catch (HttpStatusCodeException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void 필드가_줄어든_CouponCacheDto도_캐시_적중_시_정상적으로_역직렬화된다() {
        Long couponId = createCoupon("캐시확인대상");

        CouponCacheDto first = couponCacheService.getCouponCache(couponId);
        assertThat(first.status().name()).isEqualTo("ACTIVE");

        // DB를 캐시 우회로 직접 바꾼 뒤 다시 조회 — 캐시가 실제로 적중한다면
        // DB를 다시 안 보고 방금 Redis에 저장된 옛 값(ACTIVE)을 그대로 반환해야 한다
        jdbcTemplate.update("UPDATE coupon SET status='INACTIVE' WHERE id=?", couponId);
        CouponCacheDto second = couponCacheService.getCouponCache(couponId);

        assertThat(second).isEqualTo(first);
        assertThat(second.status().name()).isEqualTo("ACTIVE");
    }
}
