package com.example.auth_service.auth_service.service;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.Password;
import com.example.auth_service.auth_service.exception.ResourceNotFoundException;
import com.example.auth_service.auth_service.repository.UserRepository;
import com.example.auth_service.auth_service.repository.PasswordRepository;
import com.example.auth_service.auth_service.repository.LogRepository;
import com.example.auth_service.auth_service.domain.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordRepository passwordRepository;
    private final LogRepository logRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // 인증번호 Redis 키 접두사
    private static final String RESET_CODE_PREFIX = "RESET_CODE:";
    // 재설정 토큰 Redis 키 접두사
    private static final String RESET_TOKEN_PREFIX = "RESET_TOKEN:";
    // 코드 만료 시간 (5분)
    private static final long CODE_EXPIRATION = 300;
    // 토큰 만료 시간 (10분)
    private static final long TOKEN_EXPIRATION = 600;

    /**
     * 비밀번호 재설정 코드 생성 및 발송 - 전화번호 검증 제거
     */
    public void sendResetCode(String email) {
        // 사용자 존재 여부 확인
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // 전화번호 일치 여부 확인 부분 제거

        redisTemplate.delete(RESET_CODE_PREFIX + email);
        // 6자리 인증 코드 생성
        String code = generateRandomCode(6);

        // Redis에 인증 코드 저장 (5분 유효)
        redisTemplate.opsForValue().set(
                RESET_CODE_PREFIX + email,
                code,
                CODE_EXPIRATION,
                TimeUnit.SECONDS);

        log.info("새로운 인증 코드 생성: {} for email: {}", code, email);

        // 이메일로 인증 코드 발송
        emailService.sendPasswordResetCode(email, code);

        // 비밀번호 재설정 요청 로그 기록
        saveLog(user.getUserNo(), "PASSWORD_RESET_REQUEST",
                "비밀번호 재설정 코드 발송: " + email,
                "127.0.0.1", "Unknown");
    }

    /**
     * 재설정 코드 검증
     */
    public boolean verifyResetCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(RESET_CODE_PREFIX + email);

        log.info("Stored Code: {}, Provided Code: {}", storedCode, code);

        if (storedCode == null) {
            log.warn("사용자 {}의 인증 코드가 존재하지 않거나 만료되었습니다.", email);
            return false;
        }

        boolean isValid = storedCode.equals(code);
        log.info("인증 코드 검증 결과: {}", isValid);

        // 인증 결과 로그 기록
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            saveLog(user.getUserNo(), "PASSWORD_RESET_VERIFY",
                    "비밀번호 재설정 코드 검증 " + (isValid ? "성공" : "실패"),
                    "127.0.0.1", "Unknown");
        }

        return isValid;
    }

    /**
     * 비밀번호 재설정 토큰 생성
     */
    public String generateResetToken(String email) {
        // 인증 코드 삭제
        redisTemplate.delete(RESET_CODE_PREFIX + email);

        // 재설정 토큰 생성
        String resetToken = UUID.randomUUID().toString();

        // Redis에 토큰 저장 (10분 유효)
        redisTemplate.opsForValue().set(
                RESET_TOKEN_PREFIX + resetToken,
                email,
                TOKEN_EXPIRATION,
                TimeUnit.SECONDS);

        log.info("사용자 {}의 비밀번호 재설정 토큰이 생성되었습니다.", email);

        // 토큰 생성 로그 기록
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            saveLog(user.getUserNo(), "PASSWORD_RESET_TOKEN",
                    "비밀번호 재설정 토큰 생성",
                    "127.0.0.1", "Unknown");
        }

        return resetToken;
    }

    /**
     * 비밀번호 업데이트 - Password 테이블 사용
     */
    @Transactional
    public boolean updatePassword(String email, String resetToken, String newPassword) {
        // 토큰 검증
        String storedEmail = redisTemplate.opsForValue().get(RESET_TOKEN_PREFIX + resetToken);

        if (storedEmail == null || !storedEmail.equals(email)) {
            log.warn("유효하지 않은 재설정 토큰: {}", resetToken);
            return false;
        }

        try {
            // 사용자 정보 조회
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

            // 비밀번호 암호화 및 업데이트 - Password 테이블 사용
            String encodedPassword = passwordEncoder.encode(newPassword);

            // Password 테이블에서 사용자의 비밀번호 정보 조회
            Password password = passwordRepository.findByUser_UserNo(user.getUserNo())
                    .orElse(Password.builder()
                            .user(user)
                            .salt("")
                            .build());

            password.setPassword(encodedPassword);
            password.setUpdateDate(LocalDateTime.now());
            passwordRepository.save(password);

            // 토큰 삭제
            redisTemplate.delete(RESET_TOKEN_PREFIX + resetToken);

            log.info("사용자 {}의 비밀번호가 성공적으로 변경되었습니다.", email);

            // 비밀번호 변경 로그 기록
            saveLog(user.getUserNo(), "PASSWORD_RESET_SUCCESS",
                    "비밀번호 변경 성공",
                    "127.0.0.1", "Unknown");

            // 비밀번호 변경 알림 이메일 발송
            emailService.sendPasswordChangedNotification(email);

            return true;
        } catch (Exception e) {
            log.error("비밀번호 업데이트 중 오류 발생", e);

            // 비밀번호 변경 실패 로그 기록
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                saveLog(user.getUserNo(), "PASSWORD_RESET_FAIL",
                        "비밀번호 변경 실패: " + e.getMessage(),
                        "127.0.0.1", "Unknown");
            }
            return false;
        }
    }

    /**
     * 랜덤 숫자 코드 생성
     */
    private String generateRandomCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }

        return sb.toString();
    }

    // 로그 저장 메서드
    // 로그 저장 메서드
    private void saveLog(Long userNo, String actionType, String description, String ipAddress, String userAgent) {
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