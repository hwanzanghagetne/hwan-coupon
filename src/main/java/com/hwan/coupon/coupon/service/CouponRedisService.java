package com.hwan.coupon.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
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
        Long result = redisTemplate.execute(
                couponIssueScript,
                List.of(STOCK_KEY + couponId, ISSUED_KEY + couponId),
                String.valueOf(userId)
        );
        if (result == null) {
            throw new IllegalStateException("Redis Lua Script 실행 결과가 null입니다. couponId=" + couponId);
        }
        return result;
    }

    public boolean hasStock(Long couponId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(STOCK_KEY + couponId));
    }

    public Long getRemainingStock(Long couponId) {
        String value = redisTemplate.opsForValue().get(STOCK_KEY + couponId);
        return value == null ? null : Long.valueOf(value);
    }

    public Set<String> getIssuedUserIds(Long couponId) {
        Set<String> members = redisTemplate.opsForSet().members(ISSUED_KEY + couponId);
        return members == null ? Set.of() : members;
    }
}
