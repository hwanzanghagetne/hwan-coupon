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
            // email UNIQUE 위반만 좁혀서 변환하고, 그 외는 그대로 전파
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains("uq_member_email")) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            throw e;
        }
    }
}
