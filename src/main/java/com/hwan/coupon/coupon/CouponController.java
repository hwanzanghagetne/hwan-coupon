package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.dto.CreateCouponRequest;
import com.hwan.coupon.coupon.dto.CouponResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CouponResponse> createCoupon(@RequestBody @Valid CreateCouponRequest request) {
        return ResponseEntity.ok(couponService.createCoupon(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CouponResponse>> getCoupons() {
        return ResponseEntity.ok(couponService.getCoupons());
    }

    @GetMapping("/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CouponResponse> getCoupon(@PathVariable Long couponId) {
        return ResponseEntity.ok(couponService.getCoupon(couponId));
    }
}
