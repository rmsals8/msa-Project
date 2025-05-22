package com.example.auth_service.place_service.controller;

import com.example.auth_service.place_service.service.NaverPlaceSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowCredentials = "false")
public class NaverPlaceSearchController {
    private final NaverPlaceSearchService naverPlaceSearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/search")
    public ResponseEntity<?> searchPlace(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String query) {

        // 토큰 추출 및 검증 - ScheduleSaveController와 동일한 방식으로
        Long userId = extractUserIdFromToken(authHeader);
        if (userId == null) {
            log.warn("인증되지 않은 사용자가 장소 검색 시도: {}", query);
            Map<String, Object> response = new HashMap<>();
            response.put("status", HttpStatus.UNAUTHORIZED.value());
            response.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
            response.put("message", "인증 정보가 없습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        log.info("장소 검색 요청: 사용자 ID={}, 검색어={}", userId, query);

        try {
            // 주 API 호출
            Map<String, Object> result = naverPlaceSearchService.searchPlaces(query);

            // 실패 시 대체 메서드 호출
            if (result.containsKey("status") && result.get("status").equals("ERROR")) {
                result = naverPlaceSearchService.searchPlacesAlternative(query);
            }

            // ScheduleSaveController와 동일한 응답 형식 사용
            Map<String, Object> response = new HashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "장소 검색에 성공했습니다.");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("장소 검색 중 오류 발생: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
            response.put("message", "검색 중 오류 발생: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // JWT 토큰에서 사용자 ID 추출하는 메소드
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