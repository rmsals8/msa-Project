package com.example.schedule_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "auth-service", url = "${feign.client.config.auth-service.url}")
public interface AuthServiceClient {

    @GetMapping("/api/v1/auth/validate-token")
    Map<String, Object> validateToken(@RequestHeader("Authorization") String bearerToken);

    @GetMapping("/api/v1/usage/check/{userNo}")
    Map<String, Object> checkUsageLimit(@PathVariable Long userNo);

    @PostMapping("/api/v1/usage/increment/{userNo}")
    Map<String, Object> incrementUsage(@PathVariable Long userNo);
    
    // 추가: 사용자 구독 정보 조회
    @GetMapping("/api/v1/users/{userNo}/subscription")
    Map<String, Object> getUserSubscriptionInfo(@PathVariable Long userNo);
    
    // 또는 더 단순하게:
    @GetMapping("/api/v1/users/{userNo}/tier")
    Map<String, Object> getUserTier(@PathVariable Long userNo);
}