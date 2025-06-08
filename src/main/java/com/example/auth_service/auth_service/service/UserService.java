package com.example.auth_service.auth_service.service;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.Password;
import com.example.auth_service.auth_service.domain.RefreshToken;
import com.example.auth_service.auth_service.domain.Log;
import com.example.auth_service.auth_service.exception.BadRequestException;
import com.example.auth_service.auth_service.exception.ResourceNotFoundException;
import com.example.auth_service.auth_service.repository.UserRepository;
import com.example.auth_service.auth_service.repository.PasswordRepository;
import com.example.auth_service.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.auth_service.repository.LogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordRepository passwordRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LogRepository logRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    
    // ✅ description 최대 길이 제한 (DB 컬럼 크기에 맞춤)
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional
    public void withdrawUser(Long userNo, String password) {
        User user = userRepository.findById(userNo)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userNo));

        // 비밀번호 검증
        Password userPassword = passwordRepository.findByUser_UserNo(userNo)
                .orElseThrow(() -> new BadRequestException("사용자 인증 정보가 올바르지 않습니다."));

        if (!passwordEncoder.matches(password, userPassword.getPassword())) {
            throw new BadRequestException("비밀번호가 일치하지 않습니다.");
        }

        // 사용자 상태 변경
        user.setStatus("WITHDRAWN");
        user.setWithdrawnAt(LocalDateTime.now());
        userRepository.save(user);

        // 토큰 무효화 처리
        Optional<RefreshToken> refreshToken = refreshTokenRepository.findByUser_UserNo(userNo);
        refreshToken.ifPresent(token -> {
            redisTemplate.delete("RT:" + token.getRefreshToken());
            refreshTokenRepository.delete(token);
        });

        // 탈퇴 로그 기록 (✅ 안전한 로그 저장)
        saveLog(userNo, "USER_WITHDRAWN", "회원 탈퇴 완료", "127.0.0.1", "Unknown");
    }

    @Transactional
    public void withdrawUserSocial(Long userNo) {
        User user = userRepository.findById(userNo)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userNo));

        // 사용자 상태 변경
        user.setStatus("WITHDRAWN");
        user.setWithdrawnAt(LocalDateTime.now());
        userRepository.save(user);

        // 토큰 무효화 처리
        Optional<RefreshToken> refreshToken = refreshTokenRepository.findByUser_UserNo(userNo);
        refreshToken.ifPresent(token -> {
            redisTemplate.delete("RT:" + token.getRefreshToken());
            refreshTokenRepository.delete(token);
        });

        // 탈퇴 로그 기록 (✅ 안전한 로그 저장)
        saveLog(userNo, "USER_WITHDRAWN", "소셜 회원 탈퇴 완료", "127.0.0.1", "Unknown");
    }

    // ✅ 안전한 로그 저장 메서드 (description 길이 제한)
    private void saveLog(Long userNo, String actionType, String description, String ipAddress, String userAgent) {
        try {
            // description이 너무 길면 자르기
            String safeDescription = description;
            if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
                safeDescription = description.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
                log.warn("로그 설명이 너무 길어서 잘림: 원본길이={}, 잘린길이={}", 
                         description.length(), safeDescription.length());
            }

            // userNo로 User 객체 조회 (userNo가 null일 수 있으므로 조건부 처리)
            User user = null;
            if (userNo != null) {
                user = userRepository.findById(userNo).orElse(null);
                if (user == null) {
                    log.warn("로그 저장 시 사용자를 찾을 수 없음: userNo={}", userNo);
                }
            }

            Log logEntity = Log.builder()
                    .user(user) // User 객체 전달
                    .actionType(actionType)
                    .description(safeDescription) // 길이가 제한된 description 사용
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status("COMPLETED")
                    .createdAt(LocalDateTime.now())
                    .build();

            logRepository.save(logEntity);
            log.debug("로그 저장 완료: actionType={}, userNo={}", actionType, userNo);

        } catch (Exception e) {
            // 로그 저장 실패는 메인 로직에 영향을 주면 안 되므로 에러만 기록
            log.error("로그 저장 실패: actionType={}, userNo={}, error={}", actionType, userNo, e.getMessage());
        }
    }
}