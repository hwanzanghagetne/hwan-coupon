package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.dto.*;
import com.hwan.coupon.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final AdminBatchService adminBatchService;

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

    @PostMapping("/{couponId}/issue")
    public ResponseEntity<CouponIssueResponse> issueCoupon(
            @PathVariable Long couponId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(couponService.issueCoupon(couponId, userDetails.getMember().getId()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<MyCouponResponse>> getMyCoupons(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(couponService.getMyCoupons(userDetails.getMember().getId()));
    }

    @PostMapping("/{couponId}/use")
    public ResponseEntity<CouponIssueResponse> useCoupon(
            @PathVariable Long couponId,
            @RequestBody @Valid UseCouponRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(couponService.useCoupon(couponId, userDetails.getMember().getId(), request.orderAmount()));
    }

    @PostMapping("/{couponId}/restore")
    public ResponseEntity<CouponIssueResponse> restoreCoupon(
            @PathVariable Long couponId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(couponService.restoreCoupon(couponId, userDetails.getMember().getId()));
    }

    @GetMapping("/stats/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MonthlyStatsResponse>> getMonthlyStats(
            @RequestParam int year) {
        return ResponseEntity.ok(couponService.getMonthlyStats(year));
    }

    @PostMapping("/{couponId}/batch-issue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchIssueResponse> batchIssue(
            @PathVariable Long couponId,
            @RequestBody @Valid BatchIssueRequest request) {
        return ResponseEntity.ok(adminBatchService.requestBatch(couponId, request.userIds()));
    }

    @GetMapping("/batches/{batchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchIssueResponse> getBatchStatus(@PathVariable Long batchId) {
        return ResponseEntity.ok(adminBatchService.getBatchStatus(batchId));
    }

}
