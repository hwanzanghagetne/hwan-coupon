package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.dto.BatchIssueResponse;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.service.AdminBatchService;
import com.hwan.coupon.global.config.RabbitMQConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * 관리자 대량발급 DLQ가 실제로 동작하는지 검증한다. BatchProcessor는 처리 예외를 catch해
 * 배치를 FAILED로 기록하는데, 과거에는 그 후 메서드가 정상 반환돼 Spring AMQP가 성공으로
 * 보고 ACK해버려서 DLQ 선언이 있어도 원본 메시지가 전혀 쌓이지 않았다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class BatchProcessorDlqIntegrationTest {

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

    @Autowired private AdminBatchService adminBatchService;
    @Autowired private CouponIssueBatchRepository batchRepository;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private CouponRepository couponRepository;

    private Long couponId;
    private final List<Long> testUserIds = List.of(910_001L, 910_002L);

    @BeforeEach
    void setUp() {
        Coupon coupon = Coupon.create(
                "DLQ 테스트 쿠폰", DiscountType.FIXED, 5000,
                null, null, IssueType.ADMIN_ISSUED,
                null, null, LocalDateTime.now().plusYears(1)
        );
        couponId = couponRepository.save(coupon).getId();

        StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO member (id, email, password, name, birthdate, phone, role, created_at, updated_at) VALUES "
        );
        List<Object> params = new java.util.ArrayList<>();
        for (int i = 0; i < testUserIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(?,?,?,?,?,?,?,NOW(),NOW())");
            Long id = testUserIds.get(i);
            params.add(id);
            params.add("dlqtest" + id + "@test.com");
            params.add("password");
            params.add("dlq테스트유저" + id);
            params.add(java.time.LocalDate.of(1990, 1, 1));
            params.add("010-0000-0000");
            params.add("USER");
        }
        jdbcTemplate.update(sql.toString(), params.toArray());

        rabbitTemplate.execute(channel -> {
            channel.queuePurge(RabbitMQConfig.DLQ);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM coupon_issue WHERE coupon_id = ?", couponId);
        batchRepository.deleteAll();
        couponRepository.deleteById(couponId);
        jdbcTemplate.update("DELETE FROM member WHERE id IN (?, ?)", testUserIds.get(0), testUserIds.get(1));
    }

    @Test
    @DisplayName("처리 중 예외가 나면 배치는 FAILED로 남고 원본 메시지는 관리자 DLQ에 보관된다")
    void 처리_실패시_FAILED_및_DLQ_적재() {
        Mockito.doThrow(new RuntimeException("강제 실패"))
                .when(couponRepository).incrementIssuedQuantityBy(any(), anyInt(), any());

        BatchIssueResponse response = adminBatchService.requestBatch(couponId, testUserIds);

        await().atMost(10, TimeUnit.SECONDS).until(() ->
                batchRepository.findById(response.batchId())
                        .map(b -> b.getStatus() == BatchStatus.FAILED)
                        .orElse(false)
        );

        Message dlqMessage = rabbitTemplate.receive(RabbitMQConfig.DLQ, 5000);
        assertThat(dlqMessage).isNotNull();

        CouponIssueBatch failed = batchRepository.findById(response.batchId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(couponRepository.findById(couponId).orElseThrow().getIssuedQuantity()).isZero();
    }

    @Test
    @DisplayName("메시지 변환 자체가 실패해도 무한 재전달 대신 관리자 DLQ로 이동한다")
    void 메시지_변환_실패시_DLQ_적재() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        Message malformed = new Message("{ 이건 BatchMessagePayload로 변환될 수 없는 JSON }".getBytes(), properties);

        rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, malformed);

        Message dlqMessage = rabbitTemplate.receive(RabbitMQConfig.DLQ, 10_000);
        assertThat(dlqMessage).isNotNull();
    }

    @Test
    @DisplayName("정상 처리된 배치는 DLQ에 아무 것도 남기지 않는다")
    void 정상_처리시_DLQ_비어있음() {
        BatchIssueResponse response = adminBatchService.requestBatch(couponId, testUserIds);

        await().atMost(10, TimeUnit.SECONDS).until(() ->
                batchRepository.findById(response.batchId())
                        .map(b -> b.getStatus() == BatchStatus.DONE)
                        .orElse(false)
        );

        Message dlqMessage = rabbitTemplate.receive(RabbitMQConfig.DLQ, 1000);
        assertThat(dlqMessage).isNull();
    }
}
