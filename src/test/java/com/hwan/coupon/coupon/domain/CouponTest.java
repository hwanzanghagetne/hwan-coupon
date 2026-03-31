package com.hwan.coupon.coupon.domain;

import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

class CouponTest {

    // ---- create() 정상 케이스 ----

    @Test
    @DisplayName("정상 조건으로 생성된 쿠폰은 ACTIVE 상태이다")
    void create_정상_ACTIVE() {
        Coupon coupon = Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.FIRST_COME, null, null, LocalDateTime.now().plusDays(1));

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(coupon.getIssuedQuantity()).isZero();
        assertThat(coupon.getCreatedAt()).isNotNull();
    }

    // ---- discountValue 검증 ----

    @Test
    @DisplayName("discountValue가 0이면 COUPON_INVALID_DISCOUNT_VALUE 예외가 발생한다")
    void create_할인값_0() {
        assertThatThrownBy(() -> Coupon.create("테스트", DiscountType.FIXED, 0, null, null,
                IssueType.FIRST_COME, null, null, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_INVALID_DISCOUNT_VALUE);
    }

    @Test
    @DisplayName("discountValue가 음수이면 COUPON_INVALID_DISCOUNT_VALUE 예외가 발생한다")
    void create_할인값_음수() {
        assertThatThrownBy(() -> Coupon.create("테스트", DiscountType.FIXED, -1, null, null,
                IssueType.FIRST_COME, null, null, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_INVALID_DISCOUNT_VALUE);
    }

    @Test
    @DisplayName("RATE 타입에서 discountValue가 100이면 정상 생성된다")
    void create_정률_100_정상() {
        assertThatCode(() -> Coupon.create("테스트", DiscountType.RATE, 100, null, null,
                IssueType.FIRST_COME, null, null, LocalDateTime.now().plusDays(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RATE 타입에서 discountValue가 101이면 COUPON_INVALID_DISCOUNT_VALUE 예외가 발생한다")
    void create_정률_101_초과() {
        assertThatThrownBy(() -> Coupon.create("테스트", DiscountType.RATE, 101, null, null,
                IssueType.FIRST_COME, null, null, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_INVALID_DISCOUNT_VALUE);
    }

    @Test
    @DisplayName("FIXED 타입에서 discountValue가 101이면 정상 생성된다")
    void create_정액_101_정상() {
        assertThatCode(() -> Coupon.create("테스트", DiscountType.FIXED, 101, null, null,
                IssueType.FIRST_COME, null, null, LocalDateTime.now().plusDays(1)))
                .doesNotThrowAnyException();
    }

    // ---- expiredAt 검증 ----

    @Test
    @DisplayName("expiredAt이 현재 시각 이전이면 COUPON_INVALID_EXPIRED_AT 예외가 발생한다")
    void create_만료일_과거() {
        assertThatThrownBy(() -> Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.FIRST_COME, null, null, LocalDateTime.now().minusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_INVALID_EXPIRED_AT);
    }

    // ---- issueTime 검증 ----

    @Test
    @DisplayName("issueStartTime이 issueEndTime과 같으면 COUPON_INVALID_ISSUE_TIME 예외가 발생한다")
    void create_발급시간_start_equals_end() {
        LocalTime time = LocalTime.of(10, 0);
        assertThatThrownBy(() -> Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.FIRST_COME, time, time, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_INVALID_ISSUE_TIME);
    }

    @Test
    @DisplayName("issueStartTime이 issueEndTime보다 늦으면 COUPON_INVALID_ISSUE_TIME 예외가 발생한다")
    void create_발급시간_start_after_end() {
        assertThatThrownBy(() -> Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.FIRST_COME, LocalTime.of(12, 0), LocalTime.of(10, 0),
                LocalDateTime.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_INVALID_ISSUE_TIME);
    }

    @Test
    @DisplayName("issueStartTime만 있고 issueEndTime이 null이면 정상 생성된다")
    void create_발급시간_start만_있음() {
        assertThatCode(() -> Coupon.create("테스트", DiscountType.FIXED, 1000, null, null,
                IssueType.FIRST_COME, LocalTime.of(10, 0), null, LocalDateTime.now().plusDays(1)))
                .doesNotThrowAnyException();
    }
}