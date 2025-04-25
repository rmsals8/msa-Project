package com.example.place_service.controller;

import com.example.place_service.dto.UserPreferenceDto;
import com.example.place_service.service.UserPreferenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 카테고리 선호도 저장 API
     */
    @PostMapping("/categories")
    public ResponseEntity<?> saveCategories(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody UserPreferenceDto preferenceDto) {

        // 토큰에서 사용자 ID 추출
        Long userId = extractUserIdFromToken(authHeader);
        if (userId == null) {
            log.warn("인증되지 않은 사용자가 카테고리 선호도 저장 시도");
            Map<String, Object> response = new HashMap<>();
            response.put("status", 401);
            response.put("error", "Unauthorized");
            response.put("message", "인증 정보가 없습니다.");
            return ResponseEntity.status(401).body(response);
        }

        log.info("카테고리 선호도 저장 요청: 사용자 ID={}, 카테고리={}", userId, preferenceDto.getCategories());

        try {
            // 선호도 저장
            userPreferenceService.savePreferences(userId.toString(), preferenceDto);

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("message", "카테고리 선호도 저장 성공");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("카테고리 선호도 저장 중 오류 발생: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 500);
            response.put("error", "Internal Server Error");
            response.put("message", "카테고리 선호도 저장 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 카테고리 선호도 조회 API
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // 토큰에서 사용자 ID 추출
        Long userId = extractUserIdFromToken(authHeader);
        if (userId == null) {
            log.warn("인증되지 않은 사용자가 카테고리 선호도 조회 시도");
            Map<String, Object> response = new HashMap<>();
            response.put("status", 401);
            response.put("error", "Unauthorized");
            response.put("message", "인증 정보가 없습니다.");
            return ResponseEntity.status(401).body(response);
        }

        log.info("카테고리 선호도 조회 요청: 사용자 ID={}", userId);

        try {
            // 선호도 조회
            UserPreferenceDto preferenceDto = userPreferenceService.getPreferences(userId.toString());

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("message", "카테고리 선호도 조회 성공");
            response.put("categories", preferenceDto.getCategories());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("카테고리 선호도 조회 중 오류 발생: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 500);
            response.put("error", "Internal Server Error");
            response.put("message", "카테고리 선호도 조회 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 카테고리 선호도 삭제 API
     */
    @DeleteMapping("/categories")
    public ResponseEntity<?> deleteCategories(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // 토큰에서 사용자 ID 추출
        Long userId = extractUserIdFromToken(authHeader);
        if (userId == null) {
            log.warn("인증되지 않은 사용자가 카테고리 선호도 삭제 시도");
            Map<String, Object> response = new HashMap<>();
            response.put("status", 401);
            response.put("error", "Unauthorized");
            response.put("message", "인증 정보가 없습니다.");
            return ResponseEntity.status(401).body(response);
        }

        log.info("카테고리 선호도 삭제 요청: 사용자 ID={}", userId);

        try {
            // 선호도 삭제
            userPreferenceService.deletePreferences(userId.toString());

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("message", "카테고리 선호도 삭제 성공");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("카테고리 선호도 삭제 중 오류 발생: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 500);
            response.put("error", "Internal Server Error");
            response.put("message", "카테고리 선호도 삭제 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * JWT 토큰에서 사용자 ID 추출
     * VisitHistoryController에서 가져온 메서드를 그대로 활용
     */
    private Long extractUserIdFromToken(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return null;
            }

            // Bearer 접두사 제거
            String token = authHeader.substring(7);

            // JWT 토큰 파싱 (헤더.페이로드.서명 구조)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.error("Invalid token format");
                return null;
            }

            // Base64 디코딩하여 payload(claims) 가져오기
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

            // JSON으로 파싱
            JsonNode payloadJson = objectMapper.readTree(payload);

            // userId claim 추출
            if (payloadJson.has("userId")) {
                return payloadJson.get("userId").asLong();
            } else {
                log.warn("userId claim not found in token");
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to extract userId from token", e);
            return null;
        }
    }
}