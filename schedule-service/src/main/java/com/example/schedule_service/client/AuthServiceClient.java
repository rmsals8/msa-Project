package com.example.schedule_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/api/v1/usage/check/{userNo}")
    Map<String, Object> checkUsageLimit(@PathVariable Long userNo);

    @PostMapping("/api/v1/usage/increment/{userNo}")
    Map<String, Object> incrementUsage(@PathVariable Long userNo);
}