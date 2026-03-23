package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.dto.CouponCacheDto;
import com.hwan.coupon.global.exception.BusinessException;
import com.hwan.coupon.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponCacheService {

    private final CouponRepository couponRepository;

    @Cacheable(value = "coupon", key = "#couponId")
    @Transactional(readOnly = true)
    public CouponCacheDto getCouponCache(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        return CouponCacheDto.from(coupon);
    }

    @CacheEvict(value = "coupon", key = "#couponId")
    public void evict(Long couponId) {
    }
}
