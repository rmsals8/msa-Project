package com.example.auth_service.auth_service.service;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.Password;
import com.example.auth_service.auth_service.exception.ResourceNotFoundException;
import com.example.auth_service.auth_service.repository.UserRepository;
import com.example.auth_service.auth_service.repository.PasswordRepository;
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

/**
 * ✅ 최적화된 비밀번호 재설정 서비스
 * 비동기 이메일 발송과 로그 처리로 고성능 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordRepository passwordRepository;
    private final AsyncLogService asyncLogService;
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
     * ✅ 비밀번호 재설정 코드 생성 및 발송 (비동기 최적화)
     * 이메일 발송과 로그 저장이 비동기로 처리되어 즉시 반환
     * 
     * @param email 비밀번호를 재설정할 사용자 이메일
     * @throws ResourceNotFoundException 사용자가 존재하지 않는 경우
     */
    public void sendResetCode(String email) {
        long startTime = System.currentTimeMillis();

        // ✅ 1. 사용자 존재 여부 확인 (빠른 실패 처리)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // ✅ 2. 기존 코드 삭제 및 새 코드 생성 (중복 요청 방지)
        String existingCode = redisTemplate.opsForValue().get(RESET_CODE_PREFIX + email);
        if (existingCode != null) {
            redisTemplate.delete(RESET_CODE_PREFIX + email);
            log.debug("기존 비밀번호 재설정 코드 삭제: {}", email);
        }

        String code = generateRandomCode(6);

        // ✅ 3. Redis에 인증 코드 저장 (5분 유효)
        redisTemplate.opsForValue().set(
                RESET_CODE_PREFIX + email,
                code,
                CODE_EXPIRATION,
                TimeUnit.SECONDS);

        log.info("🔐 비밀번호 재설정 코드 생성: {} for email: {}", code, email);

        // ✅ 4. 이메일 발송 (비동기) - 즉시 반환!
        emailService.sendPasswordResetCode(email, code);

        // ✅ 5. 로그 저장 (비동기) - 성능 향상
        asyncLogService.saveLogAsync(user.getUserNo(), "PASSWORD_RESET_REQUEST",
                "비밀번호 재설정 코드 발송: " + email, "127.0.0.1", "Unknown");

        long endTime = System.currentTimeMillis();
        log.info("✅ 비밀번호 재설정 코드 발송 요청 완료: {} (처리시간: {}ms)", email, (endTime - startTime));
    }

    /**
     * ✅ 재설정 코드 검증 (성능 최적화)
     * 
     * @param email 검증할 사용자 이메일
     * @param code  사용자가 입력한 인증 코드
     * @return 코드 유효성 여부
     */
    public boolean verifyResetCode(String email, String code) {
        long startTime = System.currentTimeMillis();

        // Redis에서 저장된 코드 조회
        String storedCode = redisTemplate.opsForValue().get(RESET_CODE_PREFIX + email);

        log.debug("코드 검증 시도: email={}, 입력코드={}, 저장코드={}", email, code, storedCode);

        if (storedCode == null) {
            log.warn("❌ 사용자 {}의 인증 코드가 존재하지 않거나 만료되었습니다.", email);
            return false;
        }

        boolean isValid = storedCode.equals(code);

        // ✅ 로그 저장 (비동기) - 성능 향상
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            asyncLogService.saveLogAsync(user.getUserNo(), "PASSWORD_RESET_VERIFY",
                    "비밀번호 재설정 코드 검증 " + (isValid ? "성공" : "실패"),
                    "127.0.0.1", "Unknown");
        }

        long endTime = System.currentTimeMillis();
        if (isValid) {
            log.info("✅ 인증 코드 검증 성공: {} (처리시간: {}ms)", email, (endTime - startTime));
        } else {
            log.warn("❌ 인증 코드 검증 실패: {} (처리시간: {}ms)", email, (endTime - startTime));
        }

        return isValid;
    }

    /**
     * ✅ 비밀번호 재설정 토큰 생성 (보안 강화)
     * 
     * @param email 토큰을 생성할 사용자 이메일
     * @return 생성된 재설정 토큰
     */
    public String generateResetToken(String email) {
        long startTime = System.currentTimeMillis();

        // ✅ 인증 코드 삭제 (일회성 보장)
        redisTemplate.delete(RESET_CODE_PREFIX + email);

        // ✅ 재설정 토큰 생성 (UUID 사용으로 보안 강화)
        String resetToken = UUID.randomUUID().toString();

        // ✅ Redis에 토큰 저장 (10분 유효)
        redisTemplate.opsForValue().set(
                RESET_TOKEN_PREFIX + resetToken,
                email,
                TOKEN_EXPIRATION,
                TimeUnit.SECONDS);

        log.info("🔑 사용자 {}의 비밀번호 재설정 토큰이 생성되었습니다.", email);

        // ✅ 토큰 생성 로그 기록 (비동기)
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            asyncLogService.saveLogAsync(user.getUserNo(), "PASSWORD_RESET_TOKEN",
                    "비밀번호 재설정 토큰 생성", "127.0.0.1", "Unknown");
        }

        long endTime = System.currentTimeMillis();
        log.info("✅ 재설정 토큰 생성 완료: {} (처리시간: {}ms)", email, (endTime - startTime));

        return resetToken;
    }

    /**
     * ✅ 비밀번호 업데이트 (트랜잭션 최적화)
     * Password 테이블 사용하여 안전한 비밀번호 저장
     * 
     * @param email       사용자 이메일
     * @param resetToken  재설정 토큰
     * @param newPassword 새로운 비밀번호
     * @return 업데이트 성공 여부
     */
    @Transactional
    public boolean updatePassword(String email, String resetToken, String newPassword) {
        long startTime = System.currentTimeMillis();

        // ✅ 토큰 검증 (보안 확인)
        String storedEmail = redisTemplate.opsForValue().get(RESET_TOKEN_PREFIX + resetToken);

        if (storedEmail == null || !storedEmail.equals(email)) {
            log.warn("❌ 유효하지 않은 재설정 토큰: token={}, email={}", resetToken, email);
            return false;
        }

        try {
            // ✅ 사용자 정보 조회
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

            // ✅ 비밀번호 암호화 (BCrypt 사용)
            String encodedPassword = passwordEncoder.encode(newPassword);
            log.debug("새 비밀번호 암호화 완료: {}", email);

            // ✅ Password 테이블에서 사용자의 비밀번호 정보 조회 또는 생성
            Password password = passwordRepository.findByUser_UserNo(user.getUserNo())
                    .orElse(Password.builder()
                            .user(user)
                            .salt("") // 기본값
                            .build());

            // ✅ 비밀번호 업데이트
            password.setPassword(encodedPassword);
            password.setUpdateDate(LocalDateTime.now());
            passwordRepository.save(password);

            // ✅ 사용된 토큰 즉시 삭제 (보안 강화)
            redisTemplate.delete(RESET_TOKEN_PREFIX + resetToken);

            log.info("🔐 사용자 {}의 비밀번호가 성공적으로 변경되었습니다.", email);

            // ✅ 비밀번호 변경 성공 로그 기록 (비동기)
            asyncLogService.saveLogAsync(user.getUserNo(), "PASSWORD_RESET_SUCCESS",
                    "비밀번호 변경 성공", "127.0.0.1", "Unknown");

            // ✅ 비밀번호 변경 알림 이메일 발송 (비동기)
            emailService.sendPasswordChangedNotification(email);

            long endTime = System.currentTimeMillis();
            log.info("✅ 비밀번호 업데이트 완료: {} (처리시간: {}ms)", email, (endTime - startTime));

            return true;

        } catch (Exception e) {
            log.error("❌ 비밀번호 업데이트 중 오류 발생: email={}, error={}", email, e.getMessage(), e);

            // ✅ 비밀번호 변경 실패 로그 기록 (비동기)
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                asyncLogService.saveLogAsync(user.getUserNo(), "PASSWORD_RESET_FAIL",
                        "비밀번호 변경 실패: " + e.getMessage(), "127.0.0.1", "Unknown");
            }

            long endTime = System.currentTimeMillis();
            log.error("❌ 비밀번호 업데이트 실패: {} (처리시간: {}ms)", email, (endTime - startTime));

            return false;
        }
    }

    /**
     * ✅ 재설정 프로세스 상태 확인
     * 
     * @param email 확인할 사용자 이메일
     * @return 상태 정보 객체
     */
    public ResetStatus getResetStatus(String email) {
        // 코드 존재 여부 및 남은 시간 확인
        Long codeExpiry = redisTemplate.getExpire(RESET_CODE_PREFIX + email, TimeUnit.SECONDS);
        boolean hasActiveCode = codeExpiry != null && codeExpiry > 0;

        // 활성 토큰 개수 확인 (보안상 정확한 토큰은 반환하지 않음)
        // 실제로는 Redis SCAN을 사용하여 RESET_TOKEN_PREFIX:*로 시작하는 키들 중
        // 해당 이메일과 연결된 토큰 개수를 확인해야 함
        // 여기서는 간단한 구현을 위해 기본값 사용

        return ResetStatus.builder()
                .email(email)
                .hasActiveCode(hasActiveCode)
                .codeExpirySeconds(hasActiveCode ? codeExpiry : 0)
                .build();
    }

    /**
     * ✅ 재설정 프로세스 초기화 (관리자용)
     * 
     * @param email 초기화할 사용자 이메일
     */
    public void resetProcess(String email) {
        // 해당 이메일과 관련된 모든 Redis 키 삭제
        redisTemplate.delete(RESET_CODE_PREFIX + email);

        // 토큰 삭제는 복잡하므로 실제 운영환경에서는 Redis SCAN 사용 필요
        // 여기서는 간단한 로그만 기록
        log.info("비밀번호 재설정 프로세스 초기화: {}", email);

        // ✅ 초기화 로그 기록 (비동기)
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            asyncLogService.saveLogAsync(user.getUserNo(), "PASSWORD_RESET_CLEAR",
                    "비밀번호 재설정 프로세스 초기화", "127.0.0.1", "ADMIN");
        }
    }

    /**
     * ✅ 남은 코드 유효시간 조회 (초 단위)
     * 
     * @param email 확인할 사용자 이메일
     * @return 남은 유효시간 (초), 코드가 없으면 -1
     */
    public long getRemainingCodeTime(String email) {
        Long ttl = redisTemplate.getExpire(RESET_CODE_PREFIX + email, TimeUnit.SECONDS);
        long remainingTime = ttl != null ? ttl : -1;

        log.debug("비밀번호 재설정 코드 남은 시간: {} -> {}초", email, remainingTime);
        return remainingTime;
    }

    /**
     * ✅ 보안 강화된 랜덤 코드 생성기
     * SecureRandom 사용으로 암호학적으로 안전한 난수 생성
     * 
     * @param length 생성할 코드의 길이
     * @return 생성된 랜덤 숫자 코드
     */
    private String generateRandomCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }

        String code = sb.toString();
        log.debug("보안 랜덤 코드 생성: 길이={}, 패턴={}****", length, code.substring(0, 2));

        return code;
    }

    /**
     * ✅ 재설정 상태 정보 클래스
     */
    @lombok.Builder
    @lombok.Getter
    public static class ResetStatus {
        private final String email;
        private final boolean hasActiveCode;
        private final long codeExpirySeconds;
        private final LocalDateTime timestamp = LocalDateTime.now();
    }
}