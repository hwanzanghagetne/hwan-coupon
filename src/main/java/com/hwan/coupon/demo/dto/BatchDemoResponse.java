package com.hwan.coupon.demo.dto;

import com.hwan.coupon.coupon.domain.BatchStatus;

public record BatchDemoResponse(
        Long couponId,
        Long batchId,
        int userCount,
        BatchStatus batchStatus,
        boolean completed,
        long issuedRows,
        int issuedQuantity,
        long durationMs
) {}
