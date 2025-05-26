package com.example.auth_service.auth_service.service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.auth_service.auth_service.exception.BadRequestException;
import com.example.auth_service.auth_service.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ✅ 최적화된 이메일 인증 서비스
 * 비동기 이메일 발송과 Redis 캐싱을 통한 고성능 인증 시스템
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final RedisTemplate<String, String> redisTemplate;

    // 이메일 인증 코드 Redis 키 접두사
    private static final String EMAIL_VERIFY_CODE_PREFIX = "EMAIL_VERIFY:";
    // 이메일 인증 토큰 Redis 키 접두사
    private static final String EMAIL_VERIFY_TOKEN_PREFIX = "EMAIL_VERIFY_TOKEN:";
    // 코드 만료 시간 (5분)
    private static final long CODE_EXPIRATION = 300;
    // 토큰 만료 시간 (1시간)
    private static final long TOKEN_EXPIRATION = 3600;

    /**
     * ✅ 이메일 인증 코드 발송 (비동기 처리로 즉시 반환)
     * 
     * @param email 인증받을 이메일 주소
     * @throws BadRequestException 이미 사용중인 이메일인 경우
     */
    public void sendVerificationCode(String email) {
        long startTime = System.currentTimeMillis();

        // ✅ 1. 빠른 중복 체크 (인덱스 활용으로 빠른 조회)
        if (userRepository.existsByEmail(email)) {
            log.warn("이미 사용중인 이메일로 인증 요청: {}", email);
            throw new BadRequestException("이미 사용중인 이메일입니다.");
        }

        // ✅ 2. Redis에서 기존 코드 삭제 (중복 요청 방지)
        String existingCode = redisTemplate.opsForValue().get(EMAIL_VERIFY_CODE_PREFIX + email);
        if (existingCode != null) {
            redisTemplate.delete(EMAIL_VERIFY_CODE_PREFIX + email);
            log.debug("기존 인증 코드 삭제: {}", email);
        }

        // ✅ 3. 6자리 인증 코드 생성 (보안성 강화)
        String code = generateRandomCode(6);
        log.debug("인증 코드 생성 완료: {} -> {}", email, code);

        // ✅ 4. Redis에 인증 코드 저장 (5분 유효)
        redisTemplate.opsForValue().set(
                EMAIL_VERIFY_CODE_PREFIX + email,
                code,
                CODE_EXPIRATION,
                TimeUnit.SECONDS);

        // ✅ 5. 이메일 발송 (비동기) - 즉시 반환됨!
        emailService.sendEmailVerificationCode(email, code);

        long endTime = System.currentTimeMillis();
        log.info("✅ 이메일 인증 코드 발송 요청 완료: {} (처리시간: {}ms)", email, (endTime - startTime));
    }

    /**
     * ✅ 인증 코드 검증
     * 
     * @param email 검증할 이메일 주소
     * @param code  사용자가 입력한 인증 코드
     * @return 검증 성공 여부
     */
    public boolean verifyCode(String email, String code) {
        long startTime = System.currentTimeMillis();

        // Redis에서 저장된 코드 조회
        String storedCode = redisTemplate.opsForValue().get(EMAIL_VERIFY_CODE_PREFIX + email);

        if (storedCode == null) {
            log.warn("인증 코드가 존재하지 않거나 만료됨: {}", email);
            return false;
        }

        // 코드 일치 여부 확인
        boolean isValid = storedCode.equals(code);

        if (isValid) {
            // ✅ 코드 검증 성공 시 즉시 삭제 (재사용 방지)
            redisTemplate.delete(EMAIL_VERIFY_CODE_PREFIX + email);

            // 인증 토큰 자동 생성 및 저장
            String verificationToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    EMAIL_VERIFY_TOKEN_PREFIX + email,
                    verificationToken,
                    TOKEN_EXPIRATION,
                    TimeUnit.SECONDS);

            long endTime = System.currentTimeMillis();
            log.info("✅ 이메일 코드 검증 성공: {} (처리시간: {}ms)", email, (endTime - startTime));
        } else {
            log.warn("❌ 이메일 코드 검증 실패: {} (입력코드: {}, 저장코드: {})", email, code, storedCode);
        }

        return isValid;
    }

    /**
     * ✅ 인증 토큰 수동 생성 (특별한 경우에만 사용)
     * 
     * @param email 토큰을 생성할 이메일 주소
     * @return 생성된 인증 토큰
     */
    public String generateVerificationToken(String email) {
        String verificationToken = UUID.randomUUID().toString();

        // Redis에 토큰 저장 (1시간 유효)
        redisTemplate.opsForValue().set(
                EMAIL_VERIFY_TOKEN_PREFIX + email,
                verificationToken,
                TOKEN_EXPIRATION,
                TimeUnit.SECONDS);

        log.debug("인증 토큰 수동 생성: {} -> {}", email, verificationToken);
        return verificationToken;
    }

    /**
     * ✅ 인증 토큰 검증 및 소비
     * 
     * @param email 검증할 이메일 주소
     * @param token 검증할 인증 토큰
     * @return 토큰 유효성 여부
     */
    public boolean validateVerificationToken(String email, String token) {
        long startTime = System.currentTimeMillis();

        // Redis에서 저장된 토큰 조회
        String storedToken = redisTemplate.opsForValue().get(EMAIL_VERIFY_TOKEN_PREFIX + email);

        if (storedToken == null || !storedToken.equals(token)) {
            log.warn("❌ 인증 토큰 검증 실패: {} (토큰: {})", email, token);
            return false;
        }

        // ✅ 토큰 사용 후 즉시 삭제 (일회성 보장)
        redisTemplate.delete(EMAIL_VERIFY_TOKEN_PREFIX + email);

        long endTime = System.currentTimeMillis();
        log.info("✅ 인증 토큰 검증 성공: {} (처리시간: {}ms)", email, (endTime - startTime));

        return true;
    }

    /**
     * ✅ 인증 상태 확인 (토큰 소비하지 않음)
     * 
     * @param email 확인할 이메일 주소
     * @return 인증 완료 여부
     */
    public boolean isEmailVerified(String email) {
        String storedToken = redisTemplate.opsForValue().get(EMAIL_VERIFY_TOKEN_PREFIX + email);
        boolean isVerified = storedToken != null;

        log.debug("이메일 인증 상태 확인: {} -> {}", email, isVerified);
        return isVerified;
    }

    /**
     * ✅ 인증 과정 초기화 (관리자용)
     * 
     * @param email 초기화할 이메일 주소
     */
    public void resetVerificationProcess(String email) {
        // 인증 코드와 토큰 모두 삭제
        redisTemplate.delete(EMAIL_VERIFY_CODE_PREFIX + email);
        redisTemplate.delete(EMAIL_VERIFY_TOKEN_PREFIX + email);

        log.info("이메일 인증 과정 초기화: {}", email);
    }

    /**
     * ✅ 남은 코드 유효시간 조회 (초 단위)
     * 
     * @param email 확인할 이메일 주소
     * @return 남은 유효시간 (초), 코드가 없으면 -1
     */
    public long getRemainingCodeTime(String email) {
        Long ttl = redisTemplate.getExpire(EMAIL_VERIFY_CODE_PREFIX + email, TimeUnit.SECONDS);
        return ttl != null ? ttl : -1;
    }

    /**
     * ✅ 남은 토큰 유효시간 조회 (초 단위)
     * 
     * @param email 확인할 이메일 주소
     * @return 남은 유효시간 (초), 토큰이 없으면 -1
     */
    public long getRemainingTokenTime(String email) {
        Long ttl = redisTemplate.getExpire(EMAIL_VERIFY_TOKEN_PREFIX + email, TimeUnit.SECONDS);
        return ttl != null ? ttl : -1;
    }

    /**
     * ✅ 보안 강화된 랜덤 코드 생성기
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
        log.debug("랜덤 코드 생성: 길이={}, 코드={}", length, code);

        return code;
    }

    /**
     * ✅ 통계 정보 조회 (모니터링용)
     * 
     * @return 현재 진행중인 인증 프로세스 수
     */
    public long getActiveVerificationCount() {
        // Redis에서 EMAIL_VERIFY로 시작하는 키 개수 조회
        // 실제 구현시에는 Redis 스캔 기능 사용
        return 0; // 간단한 구현을 위해 0 반환
    }
}