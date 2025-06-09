package com.example.auth_service.auth_service.controller;

import com.example.auth_service.auth_service.domain.UserSubscription;
import com.example.auth_service.auth_service.repository.UserSubscriptionRepository;
import com.example.auth_service.auth_service.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserSubscriptionController {

    private final UserSubscriptionRepository subscriptionRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // ✅ 새로 추가: 프론트엔드에서 호출하는 구독 상태 조회 API
    @GetMapping("/subscriptions/status")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("구독 상태 조회 요청 - Authorization: {}", authHeader != null ? "있음" : "없음");

        // JWT 토큰에서 사용자 ID 추출
        Long userNo = extractUserIdFromToken(authHeader);
        if (userNo == null) {
            log.warn("인증되지 않은 사용자가 구독 상태 조회 시도");
            Map<String, Object> response = new HashMap<>();
            response.put("status", 401);
            response.put("error", "Unauthorized");
            response.put("message", "인증 정보가 없습니다.");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(401).body(response);
        }

        log.info("구독 상태 조회 - 사용자 ID: {}", userNo);

        try {
            // 활성 구독 조회
            UserSubscription subscription = subscriptionRepository
                    .findByUser_UserNoAndStatus(userNo, UserSubscription.SubscriptionStatus.ACTIVE)
                    .orElse(createDefaultFreeSubscription(userNo));

            // 응답 데이터 생성
            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("message", "구독 상태 조회 성공");
            response.put("userNo", userNo);
            response.put("planType", subscription.getPlanType().toString());
            response.put("subscriptionStatus", subscription.getStatus().toString());
            response.put("startDate", subscription.getStartDate());
            response.put("endDate", subscription.getEndDate());
            response.put("timestamp", LocalDateTime.now());

            log.info("✅ 구독 상태 조회 성공 - 사용자: {}, 플랜: {}", userNo, subscription.getPlanType());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 구독 상태 조회 중 오류 발생 - 사용자: {}, 오류: {}", userNo, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 500);
            response.put("error", "Internal Server Error");
            response.put("message", "구독 상태 조회 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(500).body(response);
        }
    }

    // 기존: 사용자 구독 정보 조회
    @GetMapping("/users/{userNo}/subscription")
    public ResponseEntity<Map<String, Object>> getUserSubscriptionInfo(@PathVariable Long userNo) {
        log.info("사용자 구독 정보 조회 - userNo: {}", userNo);

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

    // 기존: 사용자 tier만 간단히 조회
    @GetMapping("/users/{userNo}/tier")
    public ResponseEntity<Map<String, Object>> getUserTier(@PathVariable Long userNo) {
        log.info("사용자 tier 조회 - userNo: {}", userNo);

        UserSubscription subscription = subscriptionRepository
                .findByUser_UserNoAndStatus(userNo, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("userNo", userNo);
        response.put("planType", subscription != null ? subscription.getPlanType().toString() : "FREE");

        return ResponseEntity.ok(response);
    }

    // ✅ 새로 추가: JWT 토큰에서 사용자 ID 추출하는 메서드
    private Long extractUserIdFromToken(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("Authorization 헤더가 없거나 Bearer 형식이 아님");
                return null;
            }

            // Bearer 접두사 제거
            String token = authHeader.substring(7);
            log.debug("토큰 추출 완료: {}...", token.substring(0, Math.min(20, token.length())));

            // JwtTokenProvider 사용해서 userId 추출
            Long userId = jwtTokenProvider.getUserId(token);

            if (userId == null) {
                log.warn("❌ 토큰에서 userId를 찾을 수 없음");
            } else {
                log.debug("✅ 토큰에서 userId 추출 성공: {}", userId);
            }

            return userId;

        } catch (Exception e) {
            log.error("❌ 토큰에서 userId 추출 실패", e);
            return null;
        }
    }

    // 기존: 기본 FREE 구독 생성
    private UserSubscription createDefaultFreeSubscription(Long userNo) {
        log.info("기본 FREE 구독 생성 - 사용자: {}", userNo);

        return UserSubscription.builder()
                .planType(UserSubscription.PlanType.FREE)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now())
                .build();
    }
}