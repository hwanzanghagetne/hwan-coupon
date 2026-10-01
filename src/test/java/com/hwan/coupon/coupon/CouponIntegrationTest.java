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

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private Long adminId;

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
        adminId = jdbcTemplate.queryForObject("SELECT id FROM member WHERE email=?", Long.class, email);

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
    void 관리자발급_쿠폰에_totalQuantity를_넣으면_400을_받는다() {
        String base = "http://localhost:" + port;
        Map<String, Object> body = Map.of(
                "name", "수량지정관리자쿠폰", "discountType", "FIXED", "discountValue", 1000,
                "totalQuantity", 10, "issueType", "ADMIN_ISSUED", "expiredAt", "2099-01-01T00:00:00"
        );

        try {
            restTemplate.exchange(base + "/api/coupons", HttpMethod.POST, new HttpEntity<>(body, adminHeaders()), String.class);
            fail("관리자 발급 쿠폰에 수량을 넣었는데 예외가 발생하지 않았습니다");
        } catch (HttpStatusCodeException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
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
    void 잘못된_sort_필드는_500이_아니라_400을_받는다() {
        String base = "http://localhost:" + port;
        try {
            restTemplate.exchange(base + "/api/coupons?sort=unknownField", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()), String.class);
            fail("잘못된 sort인데 예외가 발생하지 않았습니다");
        } catch (HttpStatusCodeException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
        }
    }

    private void createMember(Long id) {
        jdbcTemplate.update(
                "INSERT INTO member (id, email, password, name, birthdate, phone, role, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,NOW(),NOW())",
                id, "stats-member-" + id + "@test.com", "password", "통계테스트유저" + id,
                LocalDate.of(1990, 1, 1), "010-0000-0000", "USER"
        );
    }

    // 이 테스트 클래스는 테스트 간 DB를 정리하지 않으므로, 월별 통계처럼 전체 coupon_issue를
    // 집계하는 API는 절대값이 아니라 "작업 전후 차이"로 검증해야 다른 테스트의 데이터에
    // 영향받지 않는다.
    private Map<String, Long> monthlyStatsSnapshot(int year) {
        ResponseEntity<List> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/coupons/stats/monthly?year=" + year, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), List.class);
        List<Map<String, Object>> stats = response.getBody();
        assertThat(stats).hasSize(12);
        Map<String, Long> snapshot = new java.util.HashMap<>();
        for (Map<String, Object> m : stats) {
            snapshot.put(m.get("month") + ":issued", ((Number) m.get("totalIssued")).longValue());
            snapshot.put(m.get("month") + ":used", ((Number) m.get("totalUsed")).longValue());
        }
        return snapshot;
    }

    @Test
    void 월별_통계는_발급월과_사용월을_각각_집계하고_연도_경계를_지킨다() {
        Map<String, Long> before = monthlyStatsSnapshot(2026);

        Long couponId = createCoupon("월별통계쿠폰");
        createMember(900_001L);
        createMember(900_002L);
        createMember(900_003L);

        // 1월 10일 발급, 2월 3일 사용 — issued_at 기준 1월 집계, used_at 기준 2월 집계로 분리되어야 한다
        jdbcTemplate.update(
                "INSERT INTO coupon_issue (coupon_id, user_id, status, issued_at, used_at) VALUES (?,?,?,?,?)",
                couponId, 900_001L, "USED", LocalDateTime.of(2026, 1, 10, 0, 0), LocalDateTime.of(2026, 2, 3, 0, 0));

        // 연도 시작 경계(1월 1일 00:00 포함) 검증용
        jdbcTemplate.update(
                "INSERT INTO coupon_issue (coupon_id, user_id, status, issued_at, used_at) VALUES (?,?,?,?,?)",
                couponId, 900_002L, "ISSUED", LocalDateTime.of(2026, 1, 1, 0, 0), null);
        // 다음 해 1월 1일 00:00 제외 검증용 — 별도 회원(유니크 제약 user_id+coupon_id 회피)
        jdbcTemplate.update(
                "INSERT INTO coupon_issue (coupon_id, user_id, status, issued_at, used_at) VALUES (?,?,?,?,?)",
                couponId, 900_003L, "ISSUED", LocalDateTime.of(2027, 1, 1, 0, 0), null);

        Map<String, Long> after = monthlyStatsSnapshot(2026);

        assertThat(after.get("2026-01:issued") - before.get("2026-01:issued")).isEqualTo(2);
        assertThat(after.get("2026-01:used") - before.get("2026-01:used")).isEqualTo(0);
        assertThat(after.get("2026-02:issued") - before.get("2026-02:issued")).isEqualTo(0);
        assertThat(after.get("2026-02:used") - before.get("2026-02:used")).isEqualTo(1);

        // 2027-01-01 발급 건은 2026년 어느 달에도 나타나지 않아야 한다(다음 해 경계 제외)
        long issuedDeltaAcrossYear = 0;
        for (int month = 1; month <= 12; month++) {
            String key = String.format("2026-%02d:issued", month);
            issuedDeltaAcrossYear += after.get(key) - before.get(key);
        }
        assertThat(issuedDeltaAcrossYear).isEqualTo(2);
    }

    @Test
    void 사용_후_복원된_발급은_사용_통계에서_제외된다() {
        String base = "http://localhost:" + port;
        LocalDateTime now = LocalDateTime.now();
        Map<String, Long> before = monthlyStatsSnapshot(now.getYear());

        Long couponId = createCoupon("복원통계쿠폰");
        jdbcTemplate.update(
                "INSERT INTO coupon_issue (coupon_id, user_id, status, issued_at) VALUES (?,?,?,?)",
                couponId, adminId, "ISSUED", now);

        restTemplate.postForEntity(base + "/api/coupons/" + couponId + "/use",
                new HttpEntity<>(Map.of("orderAmount", 10_000), adminHeaders()), String.class);
        restTemplate.postForEntity(base + "/api/coupons/" + couponId + "/restore",
                new HttpEntity<>(adminHeaders()), String.class);

        String usedAt = jdbcTemplate.queryForObject(
                "SELECT used_at FROM coupon_issue WHERE coupon_id=? AND user_id=?", String.class, couponId, adminId);
        assertThat(usedAt).isNull();

        Map<String, Long> after = monthlyStatsSnapshot(now.getYear());
        long usedDeltaAcrossYear = 0;
        for (int month = 1; month <= 12; month++) {
            String key = String.format("%d-%02d:used", now.getYear(), month);
            usedDeltaAcrossYear += after.get(key) - before.get(key);
        }
        assertThat(usedDeltaAcrossYear).isEqualTo(0);
    }

    @Test
    void 내_쿠폰_목록은_issuedAt_역순으로_정렬되고_쿠폰_정보가_정확히_결합된다() {
        String base = "http://localhost:" + port;
        Long couponAId = createCoupon("내쿠폰테스트A");
        Long couponBId = createCoupon("내쿠폰테스트B");
        LocalDateTime earlier = LocalDateTime.now().minusDays(1);
        LocalDateTime later = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO coupon_issue (coupon_id, user_id, status, issued_at) VALUES (?,?,?,?)",
                couponAId, adminId, "ISSUED", earlier);
        jdbcTemplate.update(
                "INSERT INTO coupon_issue (coupon_id, user_id, status, issued_at) VALUES (?,?,?,?)",
                couponBId, adminId, "ISSUED", later);

        ResponseEntity<Map> response = restTemplate.exchange(
                base + "/api/coupons/my", HttpMethod.GET, new HttpEntity<>(adminHeaders()), Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        List<Map<String, Object>> ours = content.stream()
                .filter(c -> couponAId.equals(Long.valueOf(c.get("couponId").toString()))
                        || couponBId.equals(Long.valueOf(c.get("couponId").toString())))
                .toList();

        assertThat(ours).hasSize(2);
        // issuedAt DESC이므로 나중에 발급된 B가 먼저 나와야 한다
        assertThat(ours.get(0).get("couponName")).isEqualTo("내쿠폰테스트B");
        assertThat(ours.get(1).get("couponName")).isEqualTo("내쿠폰테스트A");
        assertThat(ours.get(0).get("discountType")).isEqualTo("FIXED");
        assertThat(((Number) ours.get(0).get("discountValue")).intValue()).isEqualTo(1000);
        assertThat(ours.get(0).get("status")).isEqualTo("ISSUED");
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
