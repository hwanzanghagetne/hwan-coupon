package com.hwan.coupon.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.hwan.coupon.member.Member;
import com.hwan.coupon.member.MemberRepository;
import com.hwan.coupon.member.Role;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @PreAuthorize 권한 검증 실패가 500이 아니라 403으로 응답되는지 확인하는 회귀 테스트.
 * GlobalExceptionHandler에 AccessDeniedException 핸들러가 없으면 catch-all(Exception.class)에
 * 걸려 500으로 응답한다.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccessDeniedIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("coupon").withUsername("test").withPassword("test");

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> mysql.getJdbcUrl() + "?rewriteBatchedStatements=true&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (memberRepository.findByEmail("access-denied-test@test.com").isEmpty()) {
            memberRepository.save(Member.create("access-denied-test@test.com", passwordEncoder.encode("password123"),
                    "권한테스트유저", LocalDate.of(2000, 1, 1), "010-0000-0000", Role.USER));
        }
    }

    @Test
    void 일반유저가_관리자전용API_호출시_403을_받는다() {
        String base = "http://localhost:" + port;

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> loginBody = Map.of("email", "access-denied-test@test.com", "password", "password123");
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                base + "/api/members/login", new HttpEntity<>(loginBody, loginHeaders), String.class);

        List<String> cookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();

        HttpHeaders callHeaders = new HttpHeaders();
        callHeaders.setContentType(MediaType.APPLICATION_JSON);
        callHeaders.set(HttpHeaders.COOKIE, cookies.get(0));
        String body = "{\"name\":\"t\",\"discountType\":\"FIXED\",\"discountValue\":1,\"issueType\":\"ADMIN_ISSUED\",\"expiredAt\":\"2099-01-01T00:00:00\"}";

        try {
            restTemplate.exchange(base + "/api/coupons", HttpMethod.POST, new HttpEntity<>(body, callHeaders), String.class);
            fail("관리자 전용 API에 일반 유저가 접근했는데 예외가 발생하지 않았습니다");
        } catch (HttpStatusCodeException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(403);
            assertThat(e.getResponseBodyAsString()).contains("권한이 없습니다");
        }
    }
}
