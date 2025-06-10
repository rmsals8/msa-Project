package com.example.auth_service.place_service.controller;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import com.example.auth_service.place_service.domain.VisitHistory;
import com.example.auth_service.place_service.dto.VisitHistoryDto;
import com.example.auth_service.place_service.service.VisitHistoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/visit-histories")
@RequiredArgsConstructor
public class VisitHistoryController {

    private final VisitHistoryService visitHistoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/add")
    @CacheEvict(value = {"visitHistories", "categoryStats"}, key = "#authHeader", allEntries = true)
    public ResponseEntity<Map<String, Object>> addVisitHistory(
            @RequestBody VisitHistoryDto visitHistoryDto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        long startTime = System.currentTimeMillis();
        
        // ✅ 성능 최적화: 빠른 사용자 ID 추출
        String userId = extractUserIdFromTokenFast(authHeader);
        if (userId == null) {
            return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        try {
            VisitHistory addedHistory = visitHistoryService.addVisitHistory(visitHistoryDto, userId);
            
            long endTime = System.currentTimeMillis();
            log.info("✅ 방문 기록 추가 완료: {}ms", (endTime - startTime));

            return createSuccessResponse("방문 기록이 성공적으로 추가되었습니다.", addedHistory);
        } catch (Exception e) {
            log.error("Failed to add visit history", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "방문 기록 추가 중 오류가 발생했습니다.");
        }
    }

    // ✅ 성능 최적화: 캐시 적용된 기존 방문 기록 조회
    @GetMapping
    @Cacheable(value = "visitHistories", key = "#authHeader + '_' + (#category ?: 'all')", 
               unless = "#result.body.get('data') == null")
    public ResponseEntity<Map<String, Object>> getVisitHistories(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String category) {

        long startTime = System.currentTimeMillis();
        
        // ✅ 빠른 사용자 ID 추출 (DB 조회 없음)
        String userId = extractUserIdFromTokenFast(authHeader);
        if (userId == null) {
            return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        try {
            List<VisitHistory> histories;

            if (category != null && !category.isBlank()) {
                histories = visitHistoryService.getVisitHistoriesByCategory(userId, category);
            } else {
                histories = visitHistoryService.getVisitHistories(userId);
            }

            long endTime = System.currentTimeMillis();
            log.info("✅ 방문 기록 조회 완료: {}개, {}ms", histories.size(), (endTime - startTime));

            return createSuccessResponse("방문 기록 조회에 성공했습니다.", histories);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ 방문 기록 조회 실패: {}ms - {}", (endTime - startTime), e.getMessage());
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "방문 기록 조회 중 오류가 발생했습니다.");
        }
    }

    // ✅ 성능 최적화: 페이징 처리가 적용된 방문 기록 조회 - 캐시 적용
    @GetMapping("/paged")
    @Cacheable(value = "pagedVisitHistories", 
               key = "#authHeader + '_' + (#category ?: 'all') + '_' + #page + '_' + #size",
               unless = "#result.body.get('data') == null")
    public ResponseEntity<Map<String, Object>> getVisitHistoriesPaged(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        long startTime = System.currentTimeMillis();

        // ✅ 빠른 사용자 ID 추출
        String userId = extractUserIdFromTokenFast(authHeader);
        if (userId == null) {
            return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        try {
            // 방문 날짜 내림차순으로 정렬 - 인덱스 활용
            Pageable pageable = PageRequest.of(page, size, Sort.by("visitDate").descending());
            Page<VisitHistory> historiesPage;

            if (category != null && !category.isBlank()) {
                historiesPage = visitHistoryService.getVisitHistoriesByCategoryPaged(userId, category, pageable);
            } else {
                historiesPage = visitHistoryService.getVisitHistoriesPaged(userId, pageable);
            }

            // 페이징 정보를 포함한 응답 생성
            Map<String, Object> pageInfo = new HashMap<>();
            pageInfo.put("content", historiesPage.getContent());
            pageInfo.put("totalElements", historiesPage.getTotalElements());
            pageInfo.put("totalPages", historiesPage.getTotalPages());
            pageInfo.put("currentPage", historiesPage.getNumber());
            pageInfo.put("pageSize", historiesPage.getSize());
            pageInfo.put("last", historiesPage.isLast());
            pageInfo.put("first", historiesPage.isFirst());
            pageInfo.put("empty", historiesPage.isEmpty());

            long endTime = System.currentTimeMillis();
            log.info("✅ 페이징 방문 기록 조회 완료: {}개, {}ms", 
                historiesPage.getContent().size(), (endTime - startTime));

            return createSuccessResponse("방문 기록 조회에 성공했습니다.", pageInfo);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ 페이징 방문 기록 조회 실패: {}ms - {}", (endTime - startTime), e.getMessage());
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "방문 기록 조회 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/stats")
    @Cacheable(value = "categoryStats", key = "#authHeader", unless = "#result.body.get('data') == null")
    public ResponseEntity<Map<String, Object>> getCategoryStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // ✅ 빠른 사용자 ID 추출
        String userId = extractUserIdFromTokenFast(authHeader);
        if (userId == null) {
            return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        try {
            long startTime = System.currentTimeMillis();
            
            List<Object[]> stats = visitHistoryService.getCategoryStats(userId);

            Map<String, Long> result = new HashMap<>();
            for (Object[] stat : stats) {
                String category = (String) stat[0];
                Long count = ((Number) stat[1]).longValue();
                result.put(category, count);
            }

            long endTime = System.currentTimeMillis();
            log.info("✅ 카테고리 통계 조회 완료: {}ms", (endTime - startTime));

            return createSuccessResponse("카테고리 통계 조회에 성공했습니다.", result);
        } catch (Exception e) {
            log.error("Failed to retrieve category stats", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "카테고리 통계 조회 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = {"visitHistories", "pagedVisitHistories", "categoryStats"}, 
               key = "#authHeader", allEntries = true)
    public ResponseEntity<Map<String, Object>> deleteVisitHistory(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // ✅ 빠른 사용자 ID 추출
        String userId = extractUserIdFromTokenFast(authHeader);
        if (userId == null) {
            return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        try {
            visitHistoryService.deleteVisitHistory(id, userId);
            return createSuccessResponse("방문 기록이 성공적으로 삭제되었습니다.", null);
        } catch (IllegalArgumentException e) {
            log.warn("Visit history not found: {}", e.getMessage());
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete visit history", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "방문 기록 삭제 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping
    @CacheEvict(value = {"visitHistories", "pagedVisitHistories", "categoryStats"}, 
               key = "#authHeader", allEntries = true)
    public ResponseEntity<Map<String, Object>> deleteAllVisitHistories(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // ✅ 빠른 사용자 ID 추출
        String userId = extractUserIdFromTokenFast(authHeader);
        if (userId == null) {
            return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        try {
            visitHistoryService.deleteAllVisitHistories(userId);
            return createSuccessResponse("모든 방문 기록이 성공적으로 삭제되었습니다.", null);
        } catch (Exception e) {
            log.error("Failed to delete all visit histories", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "방문 기록 삭제 중 오류가 발생했습니다.");
        }
    }

    // ✅ 성능 최적화: JWT 토큰에서 userId만 빠르게 추출 (DB 조회 없음)
    private String extractUserIdFromTokenFast(String authHeader) {
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

            // userId claim 추출 (String으로 반환)
            if (payloadJson.has("userId")) {
                return payloadJson.get("userId").asText();
            } else {
                log.warn("userId claim not found in token");
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to extract userId from token", e);
            return null;
        }
    }

    // ✅ 기존 메소드는 레거시 호환성을 위해 유지 (하지만 사용 안 함)
    private Long extractUserIdFromToken(String authHeader) {
        String userId = extractUserIdFromTokenFast(authHeader);
        return userId != null ? Long.parseLong(userId) : null;
    }

    private ResponseEntity<Map<String, Object>> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(status).body(response);
    }
}