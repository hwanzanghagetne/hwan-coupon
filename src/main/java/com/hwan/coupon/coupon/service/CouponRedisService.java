package com.hwan.coupon.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public void rollback(Long couponId, Long userId) {
        try {
            redisTemplate.opsForValue().increment(STOCK_KEY + couponId);
            redisTemplate.opsForSet().remove(ISSUED_KEY + couponId, String.valueOf(userId));
        } catch (Exception e) {
            // rollback 실패 시 Redis-DB 불일치 발생 — 수동 복구 필요
            // DB issued_quantity 기준으로 Redis 재고를 재구성해야 함
            log.error("[Redis 정합성 오류] rollback 실패 couponId={} userId={} — DB 기준 Redis 재고 재구성 필요",
                    couponId, userId, e);
        }
    }

    public void rollbackStockOnly(Long couponId) {
        try {
            redisTemplate.opsForValue().increment(STOCK_KEY + couponId);
        } catch (Exception e) {
            log.error("[Redis 정합성 오류] 재고 rollback 실패 couponId={} — DB 기준 Redis 재고 재구성 필요",
                    couponId, e);
        }
    }

    public boolean hasStock(Long couponId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(STOCK_KEY + couponId));
    }

    public void syncStockIfAbsent(Long couponId, int remaining) {
        redisTemplate.opsForValue().setIfAbsent(STOCK_KEY + couponId, String.valueOf(remaining));
    }
}
