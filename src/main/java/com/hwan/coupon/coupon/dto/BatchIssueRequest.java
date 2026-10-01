package com.hwan.coupon.coupon.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchIssueRequest(
        @NotEmpty(message = "발급 대상 유저가 없습니다")
        @Size(max = 100_000, message = "한 번에 발급 요청 가능한 대상은 최대 100,000명입니다")
        List<@NotNull Long> userIds
) {
}