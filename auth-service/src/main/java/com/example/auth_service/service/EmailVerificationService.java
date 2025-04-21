package com.example.auth_service.service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.auth_service.exception.BadRequestException;
import com.example.auth_service.repository.UserRepository;

import lombok.RequiredArgsConstructor;

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

    public void sendVerificationCode(String email) {
        // 이메일 중복 확인
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("이미 사용중인 이메일입니다.");
        }

        // 기존 코드가 있으면 삭제
        redisTemplate.delete(EMAIL_VERIFY_CODE_PREFIX + email);

        // 6자리 인증 코드 생성
        String code = generateRandomCode(6);

        // Redis에 인증 코드 저장 (5분 유효)
        redisTemplate.opsForValue().set(
                EMAIL_VERIFY_CODE_PREFIX + email,
                code,
                CODE_EXPIRATION,
                TimeUnit.SECONDS);

        // 이메일 발송
        emailService.sendEmailVerificationCode(email, code);
    }

    public boolean verifyCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(EMAIL_VERIFY_CODE_PREFIX + email);

        if (storedCode == null) {
            return false;
        }

        boolean isValid = storedCode.equals(code);

        if (isValid) {
            // 코드 검증 성공 시 Redis에서 삭제
            redisTemplate.delete(EMAIL_VERIFY_CODE_PREFIX + email);

            // 인증 토큰 생성
            String verificationToken = UUID.randomUUID().toString();

            // Redis에 토큰 저장 (1시간 유효)
            redisTemplate.opsForValue().set(
                    EMAIL_VERIFY_TOKEN_PREFIX + email,
                    verificationToken,
                    TOKEN_EXPIRATION,
                    TimeUnit.SECONDS);

            return true;
        }

        return false;
    }

    public String generateVerificationToken(String email) {
        String verificationToken = UUID.randomUUID().toString();

        // Redis에 토큰 저장 (1시간 유효)
        redisTemplate.opsForValue().set(
                EMAIL_VERIFY_TOKEN_PREFIX + email,
                verificationToken,
                TOKEN_EXPIRATION,
                TimeUnit.SECONDS);

        return verificationToken;
    }

    public boolean validateVerificationToken(String email, String token) {
        String storedToken = redisTemplate.opsForValue().get(EMAIL_VERIFY_TOKEN_PREFIX + email);

        if (storedToken == null || !storedToken.equals(token)) {
            return false;
        }

        // 토큰 사용 후 삭제
        redisTemplate.delete(EMAIL_VERIFY_TOKEN_PREFIX + email);

        return true;
    }

    private String generateRandomCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }

        return sb.toString();
    }
}