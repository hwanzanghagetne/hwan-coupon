package com.hwan.coupon.coupon.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchIssueRequest(
        @NotEmpty(message = "발급 대상 유저가 없습니다") List<Long> userIds
) {
}