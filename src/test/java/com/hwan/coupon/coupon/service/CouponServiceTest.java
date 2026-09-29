package com.hwan.coupon.coupon.service;

import com.hwan.coupon.coupon.domain.Coupon;
import com.hwan.coupon.coupon.domain.CouponStatus;
import com.hwan.coupon.coupon.domain.DiscountType;
import com.hwan.coupon.coupon.domain.IssueType;
import static com.hwan.coupon.coupon.domain.IssueType.FIRST_COME;
import com.hwan.coupon.coupon.dto.CouponCacheDto;
import com.hwan.coupon.coupon.dto.CouponIssueAcceptedResponse;
import com.hwan.coupon.coupon.infra.FirstComeIssuePayload;
import com.hwan.coupon.coupon.repository.CouponIssueRepository;
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

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private CouponRedisService couponRedisService;

    @Mock
    private CouponCacheService couponCacheService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private MessageConverter messageConverter;

    // ---- issueCoupon ----

    @Test
    @DisplayName("ADMIN_ISSUED 쿠폰 직접 발급 시 COUPON_NOT_DIRECTLY_ISSUABLE 예외가 발생한다")
    void issueCoupon_관리자발급전용쿠폰() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, IssueType.ADMIN_ISSUED, LocalDateTime.now().plusDays(1), null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_DIRECTLY_ISSUABLE);
    }

    @Test
    @DisplayName("비활성 쿠폰 발급 시 COUPON_NOT_ACTIVE 예외가 발생한다")
    void issueCoupon_비활성쿠폰() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.INACTIVE, IssueType.FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_ACTIVE);
    }

    @Test
    @DisplayName("소진된 쿠폰 발급 시 COUPON_EXHAUSTED 예외가 발생한다")
    void issueCoupon_소진쿠폰() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.EXHAUSTED, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXHAUSTED);
    }

    @Test
    @DisplayName("만료된 쿠폰 발급 시 COUPON_EXPIRED 예외가 발생한다")
    void issueCoupon_만료쿠폰() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().minusDays(1), null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("Redis 재고 소진 시 COUPON_EXHAUSTED 예외가 발생한다")
    void issueCoupon_Redis재고소진() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
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
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(true);
        when(couponRedisService.tryIssue(1L, 1L)).thenReturn(-2L); // REDIS_RESULT_ALREADY_ISSUED

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_ISSUED);
    }

    @Test
    @DisplayName("정상 발급 요청 시 접수 응답을 반환하고 큐에 메시지를 발행한다")
    void issueCoupon_성공() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        Message convertedMessage = mock(Message.class);

        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(true);
        when(messageConverter.toMessage(eq(new FirstComeIssuePayload(1L, 1L)), any())).thenReturn(convertedMessage);
        when(couponRedisService.tryIssue(1L, 1L)).thenReturn(5L);

        CouponIssueAcceptedResponse response = couponService.issueCoupon(1L, 1L);

        assertThat(response.couponId()).isEqualTo(1L);
        assertThat(response.sendReturned()).isTrue();
        verify(rabbitTemplate).send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_FIRST_COME, convertedMessage);
    }

    @Test
    @DisplayName("Redis 재고 키가 없으면 자동 복구하지 않고 발급을 거절한다")
    void issueCoupon_Redis키없음_거절() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(false);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_STOCK_TEMPORARILY_UNAVAILABLE);

        verify(couponRedisService, never()).tryIssue(any(), any());
    }

    @Test
    @DisplayName("쿠폰 캐시 조회 중 Redis 연결 장애가 나면 503으로 응답한다")
    void issueCoupon_캐시조회중_Redis장애() {
        when(couponCacheService.getCouponCache(1L))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_STOCK_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    @DisplayName("hasStock() 확인 중 Redis 연결 장애가 나면 503으로 응답한다")
    void issueCoupon_재고확인중_Redis장애() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_STOCK_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    @DisplayName("Lua Script 실행 중 Redis 명령 타임아웃이 나면 503으로 응답한다")
    void issueCoupon_Lua실행중_Redis타임아웃() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        Message convertedMessage = mock(Message.class);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(true);
        when(messageConverter.toMessage(eq(new FirstComeIssuePayload(1L, 1L)), any())).thenReturn(convertedMessage);
        when(couponRedisService.tryIssue(1L, 1L))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("command timeout"));

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_STOCK_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    @DisplayName("Lua Script 자체의 결함으로 RedisSystemException이 나면 503으로 감추지 않고 그대로 전파한다")
    void issueCoupon_LuaScript결함_그대로전파() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        Message convertedMessage = mock(Message.class);
        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(true);
        when(messageConverter.toMessage(eq(new FirstComeIssuePayload(1L, 1L)), any())).thenReturn(convertedMessage);
        when(couponRedisService.tryIssue(1L, 1L))
                .thenThrow(new org.springframework.data.redis.RedisSystemException("script error", new RuntimeException()));

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 1L))
                .isInstanceOf(org.springframework.data.redis.RedisSystemException.class);
    }

    @Test
    @DisplayName("발행 중 연결 오류가 나면 롤백 없이 미확정 응답을 반환한다")
    void issueCoupon_발행연결오류_미확정응답() {
        CouponCacheDto cached = new CouponCacheDto(CouponStatus.ACTIVE, FIRST_COME, LocalDateTime.now().plusDays(1), null, null);
        Message convertedMessage = mock(Message.class);

        when(couponCacheService.getCouponCache(1L)).thenReturn(cached);
        when(couponRedisService.hasStock(1L)).thenReturn(true);
        when(messageConverter.toMessage(eq(new FirstComeIssuePayload(1L, 1L)), any())).thenReturn(convertedMessage);
        when(couponRedisService.tryIssue(1L, 1L)).thenReturn(5L);
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpConnectException(new RuntimeException("connection refused")))
                .when(rabbitTemplate).send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_FIRST_COME, convertedMessage);

        CouponIssueAcceptedResponse response = couponService.issueCoupon(1L, 1L);

        assertThat(response.sendReturned()).isFalse();
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
    @DisplayName("이미 ACTIVE가 아닌(INACTIVE/EXHAUSTED) 쿠폰을 비활성화해도 예외 없이 성공 처리되고 캐시는 다시 evict된다")
    void deactivateCoupon_이미비활성_멱등하게_성공() {
        Coupon coupon = Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.ADMIN_ISSUED, null, null, LocalDateTime.now().plusDays(1));
        when(couponRepository.markInactive(eq(1L), eq(CouponStatus.INACTIVE), eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(0);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        assertThatCode(() -> couponService.deactivateCoupon(1L)).doesNotThrowAnyException();

        verify(couponCacheService).evict(1L);
    }

    @Test
    @DisplayName("쿠폰 비활성화 성공 시 캐시가 evict된다")
    void deactivateCoupon_성공() {
        when(couponRepository.markInactive(eq(1L), eq(CouponStatus.INACTIVE), eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(1);

        couponService.deactivateCoupon(1L);

        verify(couponCacheService).evict(1L);
    }
}
