package com.hwan.coupon.member;

import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import com.hwan.coupon.member.dto.SignupRequest;
import com.hwan.coupon.member.dto.SignupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {

        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        Member member = Member.create(
                request.email(),
                encodedPassword,
                request.name(),
                request.birthdate(),
                request.phone(),
                Role.USER
        );

        try {
            Member saved = memberRepository.save(member);
            return new SignupResponse(saved.getId(), saved.getEmail(), saved.getName());
        } catch (DataIntegrityViolationException e) {
            // existsByEmail 체크 이후 동시 가입 요청으로 UNIQUE 제약 위반 발생 시
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }
}
