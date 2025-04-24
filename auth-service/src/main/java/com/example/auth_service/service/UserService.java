package com.example.auth_service.service;

import com.example.auth_service.domain.User;
import com.example.auth_service.domain.Password;
import com.example.auth_service.domain.RefreshToken;
import com.example.auth_service.domain.Log;
import com.example.auth_service.exception.BadRequestException;
import com.example.auth_service.exception.ResourceNotFoundException;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.repository.PasswordRepository;
import com.example.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.repository.LogRepository;

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

        // 탈퇴 로그 기록
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

        // 탈퇴 로그 기록
        saveLog(userNo, "USER_WITHDRAWN", "소셜 회원 탈퇴 완료", "127.0.0.1", "Unknown");
    }

    // 로그 저장 메서드
    private void saveLog(Long userNo, String actionType, String description, String ipAddress, String userAgent) {
        // 설명이 너무 길면 자르기
        if (description != null && description.length() > 255) {
            description = description.substring(0, 252) + "...";
        }

        // userNo로 User 객체 조회 (userNo가 null일 수 있으므로 조건부 처리)
        User user = null;
        if (userNo != null) {
            user = userRepository.findById(userNo).orElse(null);
        }

        Log log = Log.builder()
                .user(user) // User 객체 전달
                .actionType(actionType)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();

        logRepository.save(log);
    }
}