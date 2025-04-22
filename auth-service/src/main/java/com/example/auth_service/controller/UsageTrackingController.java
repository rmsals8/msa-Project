package com.example.auth_service.controller;

import com.example.auth_service.service.UsageTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageTrackingController {

    private final UsageTrackingService usageTrackingService;

    @GetMapping("/check/{userNo}")
    public ResponseEntity<Map<String, Object>> checkUsageLimit(@PathVariable Long userNo) {
        boolean canUse = usageTrackingService.canUseService(userNo);
        int remaining = usageTrackingService.getRemainingUsage(userNo);

        Map<String, Object> response = new HashMap<>();
        response.put("canUse", canUse);
        response.put("remaining", remaining);

        if (!canUse) {
            response.put("message", "일일 사용량을 초과했습니다.");
            return ResponseEntity.status(429).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/increment/{userNo}")
    public ResponseEntity<Map<String, Object>> incrementUsage(@PathVariable Long userNo) {
        boolean success = usageTrackingService.incrementUsage(userNo);
        int remaining = usageTrackingService.getRemainingUsage(userNo);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("remaining", remaining);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/remaining/{userNo}")
    public ResponseEntity<Map<String, Object>> getRemainingUsage(@PathVariable Long userNo) {
        int remaining = usageTrackingService.getRemainingUsage(userNo);

        Map<String, Object> response = new HashMap<>();
        response.put("remaining", remaining);

        return ResponseEntity.ok(response);
    }
}