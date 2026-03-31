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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

        when(memberRepository.existsByEmail("test@email.com")).thenReturn(false);
        when(passwordEncoder.encode("password1!")).thenReturn("encoded");
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        SignupResponse response = memberService.signup(request);

        assertThat(response.email()).isEqualTo("test@email.com");
        assertThat(response.name()).isEqualTo("홍길동");
        // 비밀번호 인코딩이 실제로 호출되었는지 검증
        verify(passwordEncoder).encode("password1!");
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 회원가입 시 EMAIL_ALREADY_EXISTS 예외가 발생한다")
    void signup_이메일중복() {
        SignupRequest request = new SignupRequest(
                "duplicate@email.com", "password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "010-1234-5678"
        );

        when(memberRepository.existsByEmail("duplicate@email.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        // 중복 확인 후 저장을 시도하면 안 됨
        verify(memberRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }
}