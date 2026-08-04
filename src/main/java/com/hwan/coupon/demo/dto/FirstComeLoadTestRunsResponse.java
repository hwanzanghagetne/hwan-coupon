package com.hwan.coupon.demo.dto;

import java.util.List;

public record FirstComeLoadTestRunsResponse(
        List<FirstComeLoadTestRunResponse> runs
) {}