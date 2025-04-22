package com.example.schedule_service.controller;

import com.example.auth_service.security.UserPrincipal;
import com.example.schedule_service.dto.request.SaveScheduleRequest;
import com.example.schedule_service.dto.response.SavedScheduleListResponse;
import com.example.schedule_service.dto.response.SavedScheduleResponse;
import com.example.schedule_service.service.ScheduleSaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "일정 저장", description = "일정 저장 관련 API")
public class ScheduleSaveController {
    
    private final ScheduleSaveService scheduleSaveService;
    
    @Operation(summary = "일정 저장", description = "최적화된 일정을 저장합니다.")
    @PostMapping("/save")
    public ResponseEntity<SavedScheduleResponse> saveSchedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SaveScheduleRequest request) {
        
        Long userNo = getUserNoFromUserDetails(userDetails);
        log.info("일정 저장 요청 - 사용자: {}, 일정명: {}", userNo, request.getScheduleName());
        
        SavedScheduleResponse response = scheduleSaveService.saveSchedule(userNo, request);
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "저장된 일정 목록 조회", description = "사용자의 저장된 일정 목록을 조회합니다.")
    @GetMapping("/saved")
    public ResponseEntity<List<SavedScheduleListResponse>> getSavedScheduleList(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Long userNo = getUserNoFromUserDetails(userDetails);
        log.info("저장된 일정 목록 조회 - 사용자: {}", userNo);
        
        List<SavedScheduleListResponse> response = scheduleSaveService.getUserSavedSchedules(userNo);
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "저장된 일정 상세 조회", description = "특정 저장된 일정의 상세 정보를 조회합니다.")
    @GetMapping("/saved/{scheduleId}")
    public ResponseEntity<SavedScheduleResponse> getSavedScheduleDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long scheduleId) {
        
        Long userNo = getUserNoFromUserDetails(userDetails);
        log.info("저장된 일정 상세 조회 - 사용자: {}, 일정ID: {}", userNo, scheduleId);
        
        SavedScheduleResponse response = scheduleSaveService.getSavedScheduleDetail(scheduleId, userNo);
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "저장된 일정 삭제", description = "저장된 일정을 삭제합니다.")
    @DeleteMapping("/saved/{scheduleId}")
    public ResponseEntity<Void> deleteSavedSchedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long scheduleId) {
        
        Long userNo = getUserNoFromUserDetails(userDetails);
        log.info("저장된 일정 삭제 - 사용자: {}, 일정ID: {}", userNo, scheduleId);
        
        scheduleSaveService.deleteSavedSchedule(scheduleId, userNo);
        return ResponseEntity.ok().build();
    }
    
    private Long getUserNoFromUserDetails(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("인증 정보가 없습니다");
        }
        
        if (userDetails instanceof UserPrincipal) {
            return ((UserPrincipal) userDetails).getId();
        }
        
        throw new IllegalStateException("지원하지 않는 사용자 정보 타입입니다");
    }
}