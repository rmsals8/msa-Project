package com.example.auth_service.controller;

import com.example.auth_service.service.UsageTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.CacheControl;
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

    // auth-service/src/main/java/com/example/auth_service/controller/UsageTrackingController.java

    @PostMapping("/increment/{userNo}")
    public ResponseEntity<Map<String, Object>> incrementUsage(@PathVariable Long userNo) {
        boolean success = usageTrackingService.incrementUsage(userNo);
        int remaining = usageTrackingService.getRemainingUsage(userNo);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("remaining", remaining);

        log.info("사용자 {}의 사용량 증가 - 남은 사용량: {}", userNo, remaining);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/check/{userNo}")
    public ResponseEntity<Map<String, Object>> checkUsageLimit(@PathVariable Long userNo) {
        boolean canUse = usageTrackingService.canUseService(userNo);
        int remaining = usageTrackingService.getRemainingUsage(userNo);

        Map<String, Object> response = new HashMap<>();
        response.put("canUse", canUse);
        response.put("remaining", remaining);

        log.info("사용자 {}의 사용 가능 여부 체크 - 남은 사용량: {}", userNo, remaining);

        if (!canUse) {
            response.put("message", "일일 사용량을 초과했습니다.");
            return ResponseEntity.status(429).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/remaining/{userNo}")
    public ResponseEntity<Map<String, Object>> getRemainingUsage(@PathVariable Long userNo) {
        int remaining = usageTrackingService.getRemainingUsage(userNo);

        Map<String, Object> response = new HashMap<>();
        response.put("remaining", remaining);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("Pragma", "no-cache")
                .body(response);
    }
}