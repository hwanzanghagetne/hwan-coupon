package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.dto.BatchIssueResponse;
import com.hwan.coupon.coupon.infra.BatchMessagePayload;
import com.hwan.coupon.coupon.infra.BatchProcessor;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.coupon.service.AdminBatchService;
import com.hwan.coupon.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class AdminBatchIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("coupon")
            .withUsername("test")
            .withPassword("test");

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
    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponIssueRepository couponIssueRepository;
    @Autowired private CouponIssueBatchRepository batchRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BatchProcessor batchProcessor;

    private Long couponId;
    private final List<Long> testUserIds = List.of(10001L, 10002L, 10003L, 10004L, 10005L);

    @BeforeEach
    void setUp() {
        Coupon coupon = Coupon.create(
                "배치 테스트 쿠폰", DiscountType.FIXED, 5000,
                null, null, IssueType.ADMIN_ISSUED,
                null, null, LocalDateTime.now().plusYears(1)
        );
        couponId = couponRepository.save(coupon).getId();

        // coupon_issue.user_id는 member(id)를 참조하는 FK가 걸려있어서(V3 마이그레이션),
        // 실제 member row가 없으면 INSERT IGNORE가 FK 위반을 조용히 스킵해서 발급 건수가 0으로 나온다.
        createMembers(testUserIds);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM coupon_issue WHERE coupon_id = ?", couponId);
        batchRepository.deleteAll();
        couponRepository.deleteById(couponId);
        jdbcTemplate.update("DELETE FROM member WHERE id BETWEEN 10001 AND 10005");
        jdbcTemplate.update("DELETE FROM member WHERE id BETWEEN 200001 AND 201000");
    }

    private void createMembers(List<Long> ids) {
        StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO member (id, email, password, name, birthdate, phone, role, created_at, updated_at) VALUES "
        );
        List<Object> params = new java.util.ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(?,?,?,?,?,?,?,NOW(),NOW())");
            Long id = ids.get(i);
            params.add(id);
            params.add("batchtest" + id + "@test.com");
            params.add("password");
            params.add("배치테스트유저" + id);
            params.add(java.time.LocalDate.of(1990, 1, 1));
            params.add("010-0000-0000");
            params.add("USER");
        }
        jdbcTemplate.update(sql.toString(), params.toArray());
    }

    @Test
    @DisplayName("배치 발급 요청 시 PENDING 상태로 즉시 반환된다")
    void 배치_발급_요청시_PENDING_즉시_반환() {
        BatchIssueResponse response = adminBatchService.requestBatch(couponId, testUserIds);

        assertThat(response.status()).isEqualTo(BatchStatus.PENDING);
        assertThat(response.batchId()).isNotNull();
        assertThat(response.targetCount()).isEqualTo(testUserIds.size());
        assertThat(response.completedAt()).isNull();

        // 컨슈머가 비동기로 처리 중인 배치가 남아있으면 tearDown()의 삭제와 경합해
        // FK 위반이 날 수 있어, 다른 테스트들처럼 종료 상태까지 기다린 뒤 끝낸다.
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                batchRepository.findById(response.batchId())
                        .map(b -> b.getStatus() == BatchStatus.DONE || b.getStatus() == BatchStatus.FAILED)
                        .orElse(false)
        );
    }

    @Test
    @DisplayName("비동기 처리 완료 후 DONE 상태이며 발급 이력이 정확히 N건 생성된다")
    void 배치_발급_완료시_DONE_및_발급이력_정확히_N건() {
        BatchIssueResponse response = adminBatchService.requestBatch(couponId, testUserIds);

        await().atMost(5, TimeUnit.SECONDS).until(() ->
                batchRepository.findById(response.batchId())
                        .map(b -> b.getStatus() == BatchStatus.DONE)
                        .orElse(false)
        );

        long issuedCount = couponIssueRepository.countByCouponId(couponId);
        assertThat(issuedCount).isEqualTo(testUserIds.size());

        int issuedQuantity = couponRepository.findById(couponId).orElseThrow().getIssuedQuantity();
        assertThat(issuedQuantity).isEqualTo(testUserIds.size());

        CouponIssueBatch batch = batchRepository.findById(response.batchId()).orElseThrow();
        assertThat(batch.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 ID로 요청하면 COUPON_NOT_FOUND 예외가 발생한다")
    void 존재하지_않는_쿠폰_요청시_예외() {
        assertThatThrownBy(() -> adminBatchService.requestBatch(999999L, testUserIds))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 batchId 조회 시 BATCH_NOT_FOUND 예외가 발생한다")
    void 존재하지_않는_배치_조회시_예외() {
        assertThatThrownBy(() -> adminBatchService.getBatchStatus(999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("배치를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("이미 발급받은 유저가 포함되어도 중복은 스킵되고 나머지는 정상 발급되며 배치는 DONE 처리된다")
    void 중복_유저_포함시_중복_스킵_후_DONE() {
        // 1명 선발급
        adminBatchService.requestBatch(couponId, List.of(testUserIds.get(0)));
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                couponIssueRepository.findByCouponIdAndUserId(couponId, testUserIds.get(0)).isPresent()
        );

        // 이미 발급받은 유저 포함 전체 5명 요청
        BatchIssueResponse response = adminBatchService.requestBatch(couponId, testUserIds);

        await().atMost(5, TimeUnit.SECONDS).until(() ->
                batchRepository.findById(response.batchId())
                        .map(b -> b.getStatus() == BatchStatus.DONE || b.getStatus() == BatchStatus.FAILED)
                        .orElse(false)
        );

        // INSERT IGNORE 정책: 중복은 스킵하고 나머지는 정상 삽입 → 배치는 DONE
        assertThat(batchRepository.findById(response.batchId()).orElseThrow().getStatus())
                .isEqualTo(BatchStatus.DONE);

        // 중복 1명 스킵, 나머지 4명만 신규 발급 (기존 1명 포함 총 5건)
        long issuedCount = couponIssueRepository.countByCouponId(couponId);
        assertThat(issuedCount).isEqualTo(testUserIds.size());
    }

    @Test
    @DisplayName("대용량 발급 처리 시 모든 건이 정확히 발급된다")
    void 대용량_발급_정확성_검증() {
        List<Long> largeUserIds = LongStream.rangeClosed(200_001L, 201_000L)
                .boxed()
                .toList();
        createMembers(largeUserIds);

        BatchIssueResponse response = adminBatchService.requestBatch(couponId, largeUserIds);

        await().atMost(30, TimeUnit.SECONDS).until(() ->
                batchRepository.findById(response.batchId())
                        .map(b -> b.getStatus() == BatchStatus.DONE)
                        .orElse(false)
        );

        long issuedCount = couponIssueRepository.countByCouponId(couponId);
        assertThat(issuedCount).isEqualTo(1_000);

        int issuedQuantity = couponRepository.findById(couponId).orElseThrow().getIssuedQuantity();
        assertThat(issuedQuantity).isEqualTo(1_000);
    }

    @Test
    @DisplayName("청크 경계(1,000건)를 넘는 대상도 여러 청크에 걸쳐 정확히 발급되고 배치의 issuedCount와 일치한다")
    void 청크_여러개_처리시_전체_수량_및_배치_issuedCount_일치() {
        // CHUNK_SIZE(1,000)를 넘겨서 최소 3개 청크(1000+1000+200)로 나뉘어 처리되게 한다.
        // 기존 "대용량" 테스트는 정확히 1,000건이라 청크 1개로 끝나서 청크 간 경계를 검증하지 못했다.
        List<Long> multiChunkUserIds = LongStream.rangeClosed(300_001L, 302_200L)
                .boxed()
                .toList();
        createMembers(multiChunkUserIds);

        BatchIssueResponse response = adminBatchService.requestBatch(couponId, multiChunkUserIds);

        await().atMost(30, TimeUnit.SECONDS).until(() ->
                batchRepository.findById(response.batchId())
                        .map(b -> b.getStatus() == BatchStatus.DONE)
                        .orElse(false)
        );

        long issuedCount = couponIssueRepository.countByCouponId(couponId);
        assertThat(issuedCount).isEqualTo(2_200);

        int issuedQuantity = couponRepository.findById(couponId).orElseThrow().getIssuedQuantity();
        assertThat(issuedQuantity).isEqualTo(2_200);

        // 청크마다 하나의 트랜잭션으로 issuedCount를 누적했으므로, 배치 엔티티의 issuedCount도
        // 실제 발급된 행 수와 정확히 일치해야 한다(청크 간 어중간한 상태가 없다는 증거).
        CouponIssueBatch finished = batchRepository.findById(response.batchId()).orElseThrow();
        assertThat(finished.getIssuedCount()).isEqualTo(2_200);
    }

    @Test
    @DisplayName("복구 스케줄러가 먼저 배치를 FAILED로 확정하면, 뒤늦게 도착한 청크는 롤백되고 그 판정을 덮어쓰지 않는다")
    void 복구스케줄러가_먼저_FAILED_확정하면_뒤늦은_청크는_롤백된다() {
        // 큐를 거치지 않고 배치를 직접 PROCESSING까지 선점시킨 뒤, BatchRecoveryScheduler가
        // 이미 고착으로 판단해 markFailedIfStale()로 FAILED 확정한 상황을 그대로 재현한다.
        CouponIssueBatch batch = batchRepository.save(CouponIssueBatch.create(couponId, testUserIds.size()));
        Long batchId = batch.getId();

        // updateStatusIfMatch()/markFailedIfStale()는 @Modifying 쿼리라 활성 트랜잭션이 필요한데,
        // 이 테스트 메서드 자체를 @Transactional로 만들면 BatchProcessor 내부의 각 청크
        // TransactionTemplate이 이 트랜잭션에 합류해버려서 "커밋 여부"를 검증할 수 없게 된다.
        // 그래서 이 준비 단계는 teardown()과 같은 방식으로 jdbcTemplate 직접 호출로 처리한다.
        LocalDateTime processingStartedAt = LocalDateTime.now();
        jdbcTemplate.update("UPDATE coupon_issue_batch SET status = 'PROCESSING', updated_at = ? WHERE id = ? AND status = 'PENDING'",
                processingStartedAt, batchId);

        LocalDateTime threshold = processingStartedAt.plusSeconds(1);
        int failedUpdated = jdbcTemplate.update(
                "UPDATE coupon_issue_batch SET status = 'FAILED', completed_at = ?, updated_at = ? WHERE id = ? AND status = 'PROCESSING' AND updated_at < ?",
                LocalDateTime.now(), LocalDateTime.now(), batchId, threshold);
        assertThat(failedUpdated).isEqualTo(1);

        CouponIssueBatch failedSnapshot = batchRepository.findById(batchId).orElseThrow();
        assertThat(failedSnapshot.getStatus()).isEqualTo(BatchStatus.FAILED);
        LocalDateTime failedCompletedAt = failedSnapshot.getCompletedAt();
        assertThat(failedCompletedAt).isNotNull();

        // "뒤늦게 도착한 청크"를 재현하기 위해 큐를 거치지 않고 BatchProcessor를 직접 호출한다.
        batchProcessor.processBatch(new BatchMessagePayload(batchId, couponId, testUserIds));

        // mock 반환값이 아니라 실제 DB 상태로 롤백 여부를 확인한다.
        long issuedCount = couponIssueRepository.countByCouponId(couponId);
        assertThat(issuedCount).isZero();
        int issuedQuantity = couponRepository.findById(couponId).orElseThrow().getIssuedQuantity();
        assertThat(issuedQuantity).isZero();

        // 복구 스케줄러가 남긴 FAILED 판정과 completedAt을 BatchProcessor가 덮어쓰지 않았는지 확인
        CouponIssueBatch afterProcessing = batchRepository.findById(batchId).orElseThrow();
        assertThat(afterProcessing.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(afterProcessing.getCompletedAt()).isEqualTo(failedCompletedAt);
    }
}
