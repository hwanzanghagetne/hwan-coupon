package com.hwan.coupon.coupon.infra;

import java.util.List;

public record BatchMessagePayload(
        Long batchId,
        Long couponId,
        List<Long> userIds
) {
}
