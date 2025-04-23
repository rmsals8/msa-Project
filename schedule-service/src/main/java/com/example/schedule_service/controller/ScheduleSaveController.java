package com.example.schedule_service.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.schedule_service.dto.request.SaveScheduleRequest;
import com.example.schedule_service.dto.response.SavedScheduleListResponse;
import com.example.schedule_service.dto.response.SavedScheduleResponse;
import com.example.schedule_service.service.ScheduleSaveService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedules/saved")
@RequiredArgsConstructor
public class ScheduleSaveController {

    private final ScheduleSaveService scheduleSaveService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveSchedule(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody SaveScheduleRequest request) {

        log.info("Schedule save request received: {}", request);

        // X-User-Id 헤더가 없는 경우를 대비한 예외 처리
        Long userId;
        try {
            // API Gateway에서 전달한 사용자 ID 파싱
            userId = userIdHeader != null ? Long.parseLong(userIdHeader) : null;

            if (userId == null) {
                log.warn("User ID is missing from request headers");
                return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
            }
        } catch (NumberFormatException e) {
            log.error("Invalid user ID format: {}", userIdHeader);
            return createErrorResponse(HttpStatus.BAD_REQUEST, "잘못된 사용자 ID 형식입니다.");
        }

        try {
            SavedScheduleResponse response = scheduleSaveService.saveSchedule(userId, request);
            log.info("Schedule saved successfully: {}", response.getId());
            return createSuccessResponse("일정이 성공적으로 저장되었습니다.", response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Invalid schedule request: {}", e.getMessage());
            return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to save schedule", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "일정 저장 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<Map<String, Object>> getSchedule(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long scheduleId) {

        // X-User-Id 헤더가 없는 경우를 대비한 예외 처리
        Long userId;
        try {
            userId = userIdHeader != null ? Long.parseLong(userIdHeader) : null;

            if (userId == null) {
                log.warn("User ID is missing from request headers");
                return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
            }
        } catch (NumberFormatException e) {
            log.error("Invalid user ID format: {}", userIdHeader);
            return createErrorResponse(HttpStatus.BAD_REQUEST, "잘못된 사용자 ID 형식입니다.");
        }

        log.info("Schedule retrieval request for user {} and schedule {}", userId, scheduleId);

        try {
            SavedScheduleResponse response = scheduleSaveService.getSavedScheduleDetail(scheduleId, userId);

            if (response == null) {
                return createErrorResponse(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다.");
            }

            return createSuccessResponse("일정 조회에 성공했습니다.", response);
        } catch (IllegalArgumentException e) {
            log.warn("Schedule not found: {}", e.getMessage());
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to retrieve schedule", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "일정 조회 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Map<String, Object>> deleteSchedule(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long scheduleId) {

        Long userId;
        try {
            userId = userIdHeader != null ? Long.parseLong(userIdHeader) : null;

            if (userId == null) {
                log.warn("User ID is missing from request headers");
                return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
            }
        } catch (NumberFormatException e) {
            log.error("Invalid user ID format: {}", userIdHeader);
            return createErrorResponse(HttpStatus.BAD_REQUEST, "잘못된 사용자 ID 형식입니다.");
        }

        log.info("Schedule deletion request for user {} and schedule {}", userId, scheduleId);

        try {
            scheduleSaveService.deleteSavedSchedule(scheduleId, userId);
            return createSuccessResponse("일정이 성공적으로 삭제되었습니다.", null);
        } catch (IllegalArgumentException e) {
            log.warn("Schedule not found: {}", e.getMessage());
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete schedule", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "일정 삭제 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUserSchedules(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {

        Long userId;
        try {
            userId = userIdHeader != null ? Long.parseLong(userIdHeader) : null;

            if (userId == null) {
                log.warn("User ID is missing from request headers");
                return createErrorResponse(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
            }
        } catch (NumberFormatException e) {
            log.error("Invalid user ID format: {}", userIdHeader);
            return createErrorResponse(HttpStatus.BAD_REQUEST, "잘못된 사용자 ID 형식입니다.");
        }

        log.info("User schedules retrieval request for user {}", userId);

        try {
            List<SavedScheduleListResponse> response = scheduleSaveService.getUserSavedSchedules(userId);
            return createSuccessResponse("사용자 일정 조회에 성공했습니다.", response);
        } catch (Exception e) {
            log.error("Failed to retrieve user schedules", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "사용자 일정 조회 중 오류가 발생했습니다.");
        }
    }

    // Common 패키지 없이 직접 응답 생성 메소드 구현
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