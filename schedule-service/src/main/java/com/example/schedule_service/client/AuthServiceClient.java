package com.example.schedule_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

// schedule-service/src/main/java/com/example/schedule_service/client/AuthServiceClient.java

@FeignClient(name = "auth-service", url = "${feign.client.config.auth-service.url}")
public interface AuthServiceClient {

    @GetMapping("/api/v1/auth/validate-token")
    Map<String, Object> validateToken(@RequestHeader("Authorization") String bearerToken);

    @GetMapping("/api/v1/usage/check/{userNo}")
    Map<String, Object> checkUsageLimit(@PathVariable Long userNo);

    @PostMapping("/api/v1/usage/increment/{userNo}") // GET에서 POST로 변경
    Map<String, Object> incrementUsage(@PathVariable Long userNo);
}