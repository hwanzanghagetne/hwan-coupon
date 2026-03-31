package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import com.hwan.coupon.coupon.dto.BatchIssueResponse;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class AdminBatchIntegrationTest {

    @Autowired private AdminBatchService adminBatchService;
    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponIssueRepository couponIssueRepository;
    @Autowired private CouponIssueBatchRepository batchRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

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
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM coupon_issue WHERE coupon_id = ?", couponId);
        batchRepository.deleteAll();
        couponRepository.deleteById(couponId);
    }

    @Test
    @DisplayName("배치 발급 요청 시 PENDING 상태로 즉시 반환된다")
    void 배치_발급_요청시_PENDING_즉시_반환() {
        BatchIssueResponse response = adminBatchService.requestBatch(couponId, testUserIds);

        assertThat(response.status()).isEqualTo(BatchStatus.PENDING);
        assertThat(response.batchId()).isNotNull();
        assertThat(response.targetCount()).isEqualTo(testUserIds.size());
        assertThat(response.completedAt()).isNull();
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
}
