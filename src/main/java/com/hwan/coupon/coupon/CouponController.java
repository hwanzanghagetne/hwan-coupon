package com.hwan.coupon.coupon;

import com.hwan.coupon.coupon.service.CouponService;
import com.hwan.coupon.coupon.service.AdminBatchService;

import com.hwan.coupon.coupon.dto.*;
import com.hwan.coupon.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
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
    public ResponseEntity<Page<CouponResponse>> getCoupons(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(couponService.getCoupons(pageable));
    }

    @GetMapping("/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CouponResponse> getCoupon(@PathVariable Long couponId) {
        return ResponseEntity.ok(couponService.getCoupon(couponId));
    }

    @PostMapping("/{couponId}/issue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CouponIssueResponse> issueCoupon(
            @PathVariable Long couponId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(couponService.issueCoupon(couponId, userDetails.getMember().getId()));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<MyCouponResponse>> getMyCoupons(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(couponService.getMyCoupons(userDetails.getMember().getId(), pageable));
    }

    @PostMapping("/{couponId}/use")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CouponIssueResponse> useCoupon(
            @PathVariable Long couponId,
            @RequestBody @Valid UseCouponRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(couponService.useCoupon(couponId, userDetails.getMember().getId(), request.orderAmount()));
    }

    @PostMapping("/{couponId}/restore")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CouponIssueResponse> restoreCoupon(
            @PathVariable Long couponId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(couponService.restoreCoupon(couponId, userDetails.getMember().getId()));
    }

    @GetMapping("/stats/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MonthlyStatsResponse>> getMonthlyStats(
            @RequestParam @Min(2000) @Max(2100) int year) {
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

    @PatchMapping("/{couponId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCoupon(@PathVariable Long couponId) {
        couponService.deactivateCoupon(couponId);
        return ResponseEntity.noContent().build();
    }

}
