package com.hwan.coupon.demo;

import com.hwan.coupon.demo.dto.BatchDemoRequest;
import com.hwan.coupon.demo.dto.BatchDemoResponse;
import com.hwan.coupon.demo.dto.BatchSummaryResponse;
import com.hwan.coupon.demo.dto.CouponSummaryResponse;
import com.hwan.coupon.demo.dto.FirstComeDemoRequest;
import com.hwan.coupon.demo.dto.FirstComeDemoResponse;
import com.hwan.coupon.demo.dto.FirstComeLoadTestRunsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Profile("local")
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoScenarioService demoScenarioService;
    private final LoadTestEvidenceService loadTestEvidenceService;

    @PostMapping("/first-come")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FirstComeDemoResponse> runFirstComeDemo(@RequestBody @Valid FirstComeDemoRequest request) {
        return ResponseEntity.ok(demoScenarioService.runFirstComeDemo(request));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchDemoResponse> runBatchDemo(@RequestBody @Valid BatchDemoRequest request) {
        return ResponseEntity.ok(demoScenarioService.runBatchDemo(request));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CouponSummaryResponse> getCouponSummary(@RequestParam Long couponId) {
        return ResponseEntity.ok(demoScenarioService.getCouponSummary(couponId));
    }

    @GetMapping("/batch-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchSummaryResponse> getBatchSummary(@RequestParam Long batchId) {
        return ResponseEntity.ok(demoScenarioService.getBatchSummary(batchId));
    }

    @GetMapping("/loadtest/first-come")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FirstComeLoadTestRunsResponse> getFirstComeLoadTests() {
        return ResponseEntity.ok(loadTestEvidenceService.getFirstComeRuns());
    }
}