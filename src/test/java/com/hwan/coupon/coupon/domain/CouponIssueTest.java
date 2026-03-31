package com.hwan.coupon.coupon.domain;

import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class CouponIssueTest {

    // ---- create() ----

    @Test
    @DisplayName("create()로 생성된 발급 이력의 초기 상태는 ISSUED이며 usedAt은 null이다")
    void create_초기상태_ISSUED() {
        CouponIssue issue = CouponIssue.create(1L, 10L);

        assertThat(issue.getCouponId()).isEqualTo(1L);
        assertThat(issue.getUserId()).isEqualTo(10L);
        assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(issue.getIssuedAt()).isNotNull();
        assertThat(issue.getUsedAt()).isNull();
    }

    // ---- use() ----

    @Test
    @DisplayName("ISSUED 상태에서 use() 호출 시 USED로 전이되고 usedAt이 설정된다")
    void use_성공() {
        CouponIssue issue = CouponIssue.create(1L, 10L);

        issue.use(10000, null);

        assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.USED);
        assertThat(issue.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("minOrderAmount가 null이면 주문 금액에 관계없이 사용 가능하다")
    void use_minOrderAmount_null_성공() {
        CouponIssue issue = CouponIssue.create(1L, 10L);

        assertThatCode(() -> issue.use(100, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주문 금액이 최소 주문 금액과 동일하면 사용 가능하다")
    void use_주문금액_경계값_성공() {
        CouponIssue issue = CouponIssue.create(1L, 10L);

        assertThatCode(() -> issue.use(5000, 5000)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주문 금액이 최소 주문 금액 미만이면 COUPON_NOT_APPLICABLE 예외가 발생한다")
    void use_주문금액_조건미충족() {
        CouponIssue issue = CouponIssue.create(1L, 10L);

        assertThatThrownBy(() -> issue.use(4999, 5000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE);
    }

    @Test
    @DisplayName("이미 USED 상태에서 use()를 호출하면 COUPON_ALREADY_USED 예외가 발생한다")
    void use_이미사용된쿠폰() {
        CouponIssue issue = CouponIssue.create(1L, 10L);
        issue.use(10000, null);

        assertThatThrownBy(() -> issue.use(10000, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_USED);
    }

    @Test
    @DisplayName("EXPIRED 상태에서 use()를 호출하면 COUPON_EXPIRED 예외가 발생한다")
    void use_만료된쿠폰() {
        CouponIssue issue = CouponIssue.create(1L, 10L);
        // 스케줄러가 벌크 UPDATE로 설정하는 EXPIRED 상태를 ReflectionTestUtils로 재현
        ReflectionTestUtils.setField(issue, "status", CouponIssueStatus.EXPIRED);

        assertThatThrownBy(() -> issue.use(10000, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXPIRED);
    }

    // ---- restore() ----

    @Test
    @DisplayName("USED 상태에서 restore() 호출 시 ISSUED로 전이되고 usedAt이 null이 된다")
    void restore_성공() {
        CouponIssue issue = CouponIssue.create(1L, 10L);
        issue.use(10000, null);

        issue.restore();

        assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(issue.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("ISSUED 상태에서 restore()를 호출하면 COUPON_RESTORE_NOT_ALLOWED 예외가 발생한다")
    void restore_ISSUED_상태에서_복원불가() {
        CouponIssue issue = CouponIssue.create(1L, 10L);

        assertThatThrownBy(() -> issue.restore())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_RESTORE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("EXPIRED 상태에서 restore()를 호출하면 COUPON_RESTORE_NOT_ALLOWED 예외가 발생한다")
    void restore_EXPIRED_상태에서_복원불가() {
        CouponIssue issue = CouponIssue.create(1L, 10L);
        ReflectionTestUtils.setField(issue, "status", CouponIssueStatus.EXPIRED);

        assertThatThrownBy(() -> issue.restore())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_RESTORE_NOT_ALLOWED);
    }
}