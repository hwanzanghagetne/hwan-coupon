package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponIssue;
import com.hwan.coupon.coupon.domain.CouponIssueRequest;
import com.hwan.coupon.coupon.domain.CouponIssueRequestStatus;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import static com.hwan.coupon.coupon.domain.IssueType.FIRST_COME;
import com.hwan.coupon.coupon.dto.CouponCacheDto;
import com.hwan.coupon.coupon.dto.CouponIssueAcceptedResponse;
import com.hwan.coupon.coupon.infra.FirstComeIssuePayload;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
import com.hwan.coupon.coupon.repository.CouponIssueRequestRepository;
import com.hwan.coupon.coupon.repository.CouponRepository;
import com.hwan.coupon.global.config.RabbitMQConfig;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @InjectMocks
    private CouponService couponService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponIssueRepository couponIssueRepository;

    @Mock
    private CouponIssueRequestRepository issueRequestRepository;

    @Mock
    private CouponRedisService couponRedisService;

    @Mock
    private CouponCacheService couponCacheService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    // ---- issueCoupon ----

    @Test
    @DisplayName("ADMIN_ISSUED 쿠폰 직접 발급 시 COUPON_NOT_DIRECTLY_ISSUABLE 예외가 발생한다")
    void issueCoupon_관리자발급전용쿠폰() {
        CouponCacheDto cached = new CouponCacheDto(1L, CouponStatus.ACTIVE, IssueType.ADMIN_ISSUED, LocalDateTime.now().plusDays(1), null, null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_DIRECTLY_ISSUABLE);
    }

    @Test
    @DisplayName("비활성 쿠폰 발급 시 COUPON_NOT_ACTIVE 예외가 발생한다")
    void issueCoupon_비활성쿠폰() {
        CouponCacheDto cached = new CouponCacheDto(1L, CouponStatus.INACTIVE, IssueType.FIRST_COME, LocalDateTime.now().plusDays(1), null, null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_ACTIVE);
    }

    @Test
    @DisplayName("소진된 쿠폰 발급 시 COUPON_EXHAUSTED 예외가 발생한다")
    void issueCoupon_소진쿠폰() {
        CouponCacheDto cached = new CouponCacheDto(1L, CouponStatus.EXHAUSTED, FIRST_COME, LocalDateTime.now().plusDays(1), null, null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXHAUSTED);
    }

    @Test
    @DisplayName("만료된 쿠폰 발급 시 COUPON_EXPIRED 예외가 발생한다")
    void issueCoupon_만료쿠폰() {
        CouponCacheDto cached = new CouponCacheDto(1L, CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().minusDays(1), null, null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("Redis 재고 소진 시 COUPON_EXHAUSTED 예외가 발생한다")
    void issueCoupon_Redis재고소진() {
        CouponCacheDto cached = new CouponCacheDto(1L, CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(true);
        when(couponRedisService.tryIssue(1L, 1L)).thenReturn(-1L); // REDIS_RESULT_EXHAUSTED

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXHAUSTED);
    }

    @Test
    @DisplayName("중복 발급 시도 시 COUPON_ALREADY_ISSUED 예외가 발생한다")
    void issueCoupon_중복발급() {
        CouponCacheDto cached = new CouponCacheDto(1L, CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(true);
        when(couponRedisService.tryIssue(1L, 1L)).thenReturn(-2L); // REDIS_RESULT_ALREADY_ISSUED

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_ISSUED);
    }

    @Test
    @DisplayName("정상 발급 요청 시 접수 상태(PENDING)를 반환하고 큐에 메시지를 발행한다")
    void issueCoupon_성공() {
        CouponCacheDto cached = new CouponCacheDto(1L, CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null, null);
        CouponIssueRequest savedRequest = CouponIssueRequest.create(1L, 1L);
        ReflectionTestUtils.setField(savedRequest, "id", 10L);

        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(true);
        when(couponRedisService.tryIssue(1L, 1L)).thenReturn(5L);
        when(issueRequestRepository.save(any())).thenReturn(savedRequest);

        CouponIssueAcceptedResponse response = couponService.issueCoupon(1L, 1L);

        assertThat(response.requestId()).isEqualTo(10L);
        assertThat(response.couponId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(CouponIssueRequestStatus.PENDING);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_FIRST_COME),
                any(FirstComeIssuePayload.class)
        );
    }

    // ---- useCoupon ----

    @Test
    @DisplayName("존재하지 않는 쿠폰 사용 시 COUPON_NOT_FOUND 예외가 발생한다")
    void useCoupon_쿠폰없음() {
        when(couponRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.useCoupon(1L, 1L, 10000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("만료된 쿠폰 사용 시 COUPON_EXPIRED 예외가 발생한다")
    void useCoupon_만료쿠폰() {
        Coupon coupon = Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.ADMIN_ISSUED, null, null, LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(coupon, "expiredAt", LocalDateTime.now().minusDays(1));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.useCoupon(1L, 1L, 10000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("발급 이력이 없는 쿠폰 사용 시 COUPON_ISSUE_NOT_FOUND 예외가 발생한다")
    void useCoupon_발급이력없음() {
        Coupon coupon = Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.ADMIN_ISSUED, null, null, LocalDateTime.now().plusDays(1));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponIssueRepository.findByCouponIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.useCoupon(1L, 1L, 10000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ISSUE_NOT_FOUND);
    }

    // ---- deactivateCoupon ----

    @Test
    @DisplayName("존재하지 않는 쿠폰 비활성화 시 COUPON_NOT_FOUND 예외가 발생한다")
    void deactivateCoupon_쿠폰없음() {
        when(couponRepository.markInactive(eq(1L), eq(CouponStatus.INACTIVE), eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(0);
        when(couponRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.deactivateCoupon(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 비활성화된 쿠폰 비활성화 시 COUPON_ALREADY_INACTIVE 예외가 발생한다")
    void deactivateCoupon_이미비활성() {
        Coupon coupon = Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.ADMIN_ISSUED, null, null, LocalDateTime.now().plusDays(1));
        when(couponRepository.markInactive(eq(1L), eq(CouponStatus.INACTIVE), eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(0);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.deactivateCoupon(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_INACTIVE);
    }

    @Test
    @DisplayName("쿠폰 비활성화 성공 시 캐시가 evict된다")
    void deactivateCoupon_성공() {
        when(couponRepository.markInactive(eq(1L), eq(CouponStatus.INACTIVE), eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(1);

        couponService.deactivateCoupon(1L);

        verify(couponCacheService).evict(1L);
    }
}
