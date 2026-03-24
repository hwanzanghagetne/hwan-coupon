package com.hwan.coupon.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponRedisService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> couponIssueScript;

    private static final String STOCK_KEY = "coupon:stock:";
    private static final String ISSUED_KEY = "coupon:issued:";

    public void initStock(Long couponId, int totalQuantity) {
        redisTemplate.opsForValue().set(STOCK_KEY + couponId, String.valueOf(totalQuantity));
    }

    public long tryIssue(Long couponId, Long userId) {
        return redisTemplate.execute(
                couponIssueScript,
                List.of(STOCK_KEY + couponId, ISSUED_KEY + couponId),
                String.valueOf(userId)
        );
    }

    public void rollback(Long couponId, Long userId) {
        redisTemplate.opsForValue().increment(STOCK_KEY + couponId);
        redisTemplate.opsForSet().remove(ISSUED_KEY + couponId, String.valueOf(userId));
    }

    public boolean hasStock(Long couponId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(STOCK_KEY + couponId));
    }

    public void syncStockIfAbsent(Long couponId, int remaining) {
        redisTemplate.opsForValue().setIfAbsent(STOCK_KEY + couponId, String.valueOf(remaining));
    }
}
