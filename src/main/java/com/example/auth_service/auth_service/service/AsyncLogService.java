package com.example.auth_service.auth_service.service;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.Log;
import com.example.auth_service.auth_service.repository.UserRepository;
import com.example.auth_service.auth_service.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ✅ 비동기 로그 저장 서비스
 * 메인 비즈니스 로직의 성능에 영향을 주지 않도록 로그를 백그라운드에서 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncLogService {

    private final UserRepository userRepository;
    private final LogRepository logRepository;

    /**
     * ✅ 비동기 로그 저장 메서드
     * 메인 스레드를 차단하지 않고 백그라운드에서 로그를 저장
     * 
     * @param userNo      사용자 번호 (null 가능)
     * @param actionType  행동 타입 (예: LOGIN_SUCCESS, SIGNUP, PASSWORD_RESET 등)
     * @param description 상세 설명
     * @param ipAddress   클라이언트 IP 주소
     * @param userAgent   사용자 에이전트 정보
     */
    @Async("logTaskExecutor")
    public void saveLogAsync(Long userNo, String actionType, String description,
            String ipAddress, String userAgent) {
        try {
            long startTime = System.currentTimeMillis();

            // 설명이 너무 길면 DB 제약사항에 맞게 자르기
            if (description != null && description.length() > 255) {
                description = description.substring(0, 252) + "...";
                log.debug("로그 설명이 너무 길어서 잘림: userNo={}", userNo);
            }

            // 사용자 정보 조회 (userNo가 null일 수 있으므로 조건부 처리)
            User user = null;
            if (userNo != null) {
                user = userRepository.findById(userNo).orElse(null);
                if (user == null) {
                    log.warn("로그 저장 시 사용자를 찾을 수 없음: userNo={}", userNo);
                }
            }

            // 로그 엔티티 생성 및 저장
            Log logEntity = Log.builder()
                    .user(user)
                    .actionType(actionType)
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status("COMPLETED")
                    .createdAt(LocalDateTime.now())
                    .build();

            logRepository.save(logEntity);

            long endTime = System.currentTimeMillis();
            log.debug("✅ 비동기 로그 저장 완료: userNo={}, actionType={} (처리시간: {}ms)",
                    userNo, actionType, (endTime - startTime));

        } catch (Exception e) {
            log.error("❌ 비동기 로그 저장 실패: userNo={}, actionType={}, error={}",
                    userNo, actionType, e.getMessage(), e);
            // 비동기 로그 저장 실패는 메인 비즈니스 로직에 영향을 주지 않음
        }
    }

    /**
     * ✅ 간편한 로그 저장 메서드 (IP와 UserAgent 기본값 사용)
     * 
     * @param userNo      사용자 번호
     * @param actionType  행동 타입
     * @param description 상세 설명
     */
    @Async("logTaskExecutor")
    public void saveLogAsync(Long userNo, String actionType, String description) {
        saveLogAsync(userNo, actionType, description, "127.0.0.1", "Unknown");
    }

    /**
     * ✅ 사용자 없이 로그 저장 (시스템 로그용)
     * 
     * @param actionType  행동 타입
     * @param description 상세 설명
     */
    @Async("logTaskExecutor")
    public void saveSystemLogAsync(String actionType, String description) {
        saveLogAsync(null, actionType, description, "SYSTEM", "SYSTEM");
    }
}