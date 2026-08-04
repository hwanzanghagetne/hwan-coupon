package com.hwan.coupon.demo.dto;

import java.time.LocalDateTime;

public record FirstComeLoadTestRunResponse(
        String fileName,
        String label,
        LocalDateTime lastModifiedAt,
        int vus,
        int httpRequests,
        double avgMs,
        double p90Ms,
        double p95Ms,
        int successCount,
        int duplicateCount,
        int exhaustedCount,
        int otherCount,
        double failedRate,
        long durationMs
) {}