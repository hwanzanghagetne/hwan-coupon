package com.hwan.coupon.member;

import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import com.hwan.coupon.member.dto.SignupRequest;
import com.hwan.coupon.member.dto.SignupResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원가입 성공 시 저장된 이메일과 이름을 담은 응답을 반환한다")
    void signup_성공() {
        SignupRequest request = new SignupRequest(
                "test@email.com", "password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "010-1234-5678"
        );
        Member saved = Member.create(
                "test@email.com", "encoded", "홍길동",
                LocalDate.of(1990, 1, 1), "010-1234-5678",
                Role.USER
        );

        when(passwordEncoder.encode("password1!")).thenReturn("encoded");
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        SignupResponse response = memberService.signup(request);

        assertThat(response.email()).isEqualTo("test@email.com");
        assertThat(response.name()).isEqualTo("홍길동");
        // 비밀번호 인코딩이 실제로 호출되었는지 검증
        verify(passwordEncoder).encode("password1!");
    }

    @Test
    @DisplayName("email UNIQUE 제약을 위반하면 EMAIL_ALREADY_EXISTS로 변환된다")
    void signup_이메일중복시_EMAIL_ALREADY_EXISTS_예외가_발생한다() {
        SignupRequest request = new SignupRequest(
                "duplicate@email.com", "password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "010-1234-5678"
        );
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("Duplicate entry 'duplicate@email.com' for key 'member.uq_member_email'")
        );

        when(passwordEncoder.encode("password1!")).thenReturn("encoded");
        when(memberRepository.save(any(Member.class))).thenThrow(violation);

        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("email UNIQUE 위반이 아닌 무결성 제약 위반은 이메일 중복으로 위장하지 않고 그대로 전파된다")
    void signup_이메일외_무결성위반은_그대로_전파() {
        SignupRequest request = new SignupRequest(
                "normal@email.com", "password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "010-1234-5678"
        );
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("Data too long for column 'name' at row 1")
        );

        when(passwordEncoder.encode("password1!")).thenReturn("encoded");
        when(memberRepository.save(any(Member.class))).thenThrow(violation);

        assertThatThrownBy(() -> memberService.signup(request))
                .isSameAs(violation);
    }
}
