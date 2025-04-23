package com.example.auth_service.controller;

import com.example.auth_service.domain.UserSubscription;
import com.example.auth_service.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserSubscriptionController {

    private final UserSubscriptionRepository subscriptionRepository;

    // 사용자 구독 정보 조회
    @GetMapping("/{userNo}/subscription")
    public ResponseEntity<Map<String, Object>> getUserSubscriptionInfo(@PathVariable Long userNo) {
        UserSubscription subscription = subscriptionRepository
                .findByUser_UserNoAndStatus(userNo, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElse(createDefaultFreeSubscription(userNo));

        Map<String, Object> response = new HashMap<>();
        response.put("userNo", userNo);
        response.put("planType", subscription.getPlanType().toString());
        response.put("status", subscription.getStatus().toString());
        response.put("startDate", subscription.getStartDate());
        response.put("endDate", subscription.getEndDate());

        return ResponseEntity.ok(response);
    }

    // 사용자 tier만 간단히 조회
    @GetMapping("/{userNo}/tier")
    public ResponseEntity<Map<String, Object>> getUserTier(@PathVariable Long userNo) {
        UserSubscription subscription = subscriptionRepository
                .findByUser_UserNoAndStatus(userNo, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("userNo", userNo);
        response.put("planType", subscription != null ? subscription.getPlanType().toString() : "FREE");

        return ResponseEntity.ok(response);
    }

    private UserSubscription createDefaultFreeSubscription(Long userNo) {
        // 기본 FREE 구독 생성 로직
        return UserSubscription.builder()
                .planType(UserSubscription.PlanType.FREE)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .build();
    }
}