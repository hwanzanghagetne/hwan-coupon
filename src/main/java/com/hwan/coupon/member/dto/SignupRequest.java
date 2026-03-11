package com.hwan.coupon.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SignupRequest(

        @Email
        @NotBlank
        String email,

        @NotBlank
        String password,

        @NotBlank
        String name,

        @NotNull
        LocalDate birthdate,

        @NotBlank
        String phone
) {}
