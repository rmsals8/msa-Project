package com.example.auth_service.auth_service.service;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.UserAgreement;
import com.example.auth_service.auth_service.dto.request.auth.CompleteSignupRequest;
import com.example.auth_service.auth_service.dto.request.auth.LoginRequest;
import com.example.auth_service.auth_service.dto.request.auth.TokenRefreshRequest;
import com.example.auth_service.auth_service.dto.response.auth.AuthResponse;
import com.example.auth_service.auth_service.dto.response.auth.UserProfile;
import com.example.auth_service.auth_service.domain.Password;
import com.example.auth_service.auth_service.domain.RefreshToken;
import com.example.auth_service.auth_service.exception.BadRequestException;
import com.example.auth_service.auth_service.exception.ResourceNotFoundException;
import com.example.auth_service.auth_service.repository.UserRepository;
import com.example.auth_service.auth_service.repository.PasswordRepository;
import com.example.auth_service.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.auth_service.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordRepository passwordRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailVerificationService emailVerificationService;

    // ✅ 회원가입 처리 (기존 유지)
    @Transactional
    public AuthResponse completeSignup(CompleteSignupRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("회원가입 시작: {}", request.getEmail());

        try {
            // 1. 빠른 검증
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("이미 사용중인 이메일입니다.");
            }

            // 2. 인증 토큰 검증
            if (!emailVerificationService.validateVerificationToken(
                    request.getEmail(), request.getVerificationToken())) {
                throw new BadRequestException("유효하지 않은 인증 토큰입니다.");
            }

            // 3. 비밀번호 암호화
            String encodedPassword = passwordEncoder.encode(request.getPassword());

            // 4. 사용자 생성 및 저장
            User user = User.builder()
                    .userName(request.getName())
                    .email(request.getEmail())
                    .loginType(0)
                    .status("ACTIVE")
                    .build();

            UserAgreement userAgreement = UserAgreement.builder()
                    .user(user)
                    .termsAgreed(request.isTermsAgreed())
                    .marketingAgreed(request.isMarketingAgreed())
                    .createdAt(LocalDateTime.now())
                    .build();
            user.setUserAgreement(userAgreement);

            User savedUser = userRepository.save(user);

            // 5. 비밀번호 저장
            String salt = generateSalt();
            Password password = Password.builder()
                    .user(savedUser)
                    .salt(salt)
                    .password(encodedPassword)
                    .updateDate(LocalDateTime.now())
                    .build();
            passwordRepository.save(password);

            // 6. 토큰 생성 (성능 최적화된 버전 사용)
            String accessToken = tokenProvider.createToken(savedUser.getEmail(), savedUser.getUserNo(), 0);
            String refreshToken = tokenProvider.createRefreshToken(savedUser.getEmail(), savedUser.getUserNo(), 0);

            // 7. RefreshToken 저장
            saveRefreshToken(savedUser, refreshToken);

            long endTime = System.currentTimeMillis();
            log.info("✅ 회원가입 완료: {} ({}ms)", request.getEmail(), (endTime - startTime));

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getTokenValidityInMilliseconds())
                    .userProfile(createUserProfile(savedUser))
                    .isSuccess(true)
                    .build();

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ 회원가입 실패: {} ({}ms) - {}", 
                request.getEmail(), (endTime - startTime), e.getMessage());
            throw e;
        }
    }

    // ✅ 로그인 성능 대폭 개선
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("로그인 시작: {}", request.getEmail());

        try {
            // ✅ 1. 간단한 사용자 조회 (Password만 fetch)
            User user = userRepository.findByEmailForLogin(request.getEmail())
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 사용자입니다."));

            long userQueryTime = System.currentTimeMillis();
            log.debug("사용자 조회 완료: {}ms", (userQueryTime - startTime));

            // ✅ 2. 사용자 상태 체크 (빠른 실패)
            if ("WITHDRAWN".equals(user.getStatus())) {
                throw new BadRequestException("탈퇴한 회원입니다.");
            }

            // ✅ 3. 소셜 로그인 사용자 체크
            if (user.getLoginType() != null && user.getLoginType() == 1) {
                throw new BadRequestException("소셜 로그인 사용자입니다. 소셜 로그인을 이용해주세요.");
            }

            // ✅ 4. 비밀번호 체크
            if (!user.hasPassword()) {
                throw new BadRequestException("비밀번호가 설정되지 않은 사용자입니다.");
            }

            // ✅ 5. 비밀번호 검증 (가장 마지막에)
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BadRequestException("비밀번호가 일치하지 않습니다.");
            }

            long authTime = System.currentTimeMillis();
            log.debug("인증 완료: {}ms", (authTime - userQueryTime));

            // ✅ 6. 토큰 생성 (DB 조회 없는 최적화된 버전)
            String accessToken = tokenProvider.createToken(user.getEmail(), user.getUserNo(), user.getLoginType());
            String refreshToken = tokenProvider.createRefreshToken(user.getEmail(), user.getUserNo(), user.getLoginType());

            // ✅ 7. RefreshToken 저장
            saveRefreshToken(user, refreshToken);

            long endTime = System.currentTimeMillis();
            log.info("✅ 로그인 완료: {} ({}ms)", request.getEmail(), (endTime - startTime));

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getTokenValidityInMilliseconds())
                    .userProfile(createUserProfile(user))
                    .isSuccess(true)
                    .build();

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ 로그인 실패: {} ({}ms) - {}", 
                request.getEmail(), (endTime - startTime), e.getMessage());
            throw e;
        }
    }

    // ✅ 토큰 갱신 처리 (성능 최적화)
    @Transactional(readOnly = true)
    public AuthResponse refresh(TokenRefreshRequest request) {
        try {
            // Redis에서 리프레시 토큰 검증
            String savedToken = redisTemplate.opsForValue().get("RT:" + request.getRefreshToken());
            if (savedToken == null) {
                throw new BadRequestException("Invalid refresh token");
            }

            // 토큰에서 사용자 정보 추출
            String username = tokenProvider.getUsername(request.getRefreshToken());
            Long userId = tokenProvider.getUserId(request.getRefreshToken());

            // ✅ DB 조회 최소화
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", username));

            // ✅ 새 토큰 생성 (DB 조회 없는 버전 사용)
            String newAccessToken = tokenProvider.createToken(username, userId, user.getLoginType());
            String newRefreshToken = tokenProvider.createRefreshToken(username, userId, user.getLoginType());

            // Redis 업데이트
            redisTemplate.delete("RT:" + request.getRefreshToken());
            redisTemplate.opsForValue().set(
                    "RT:" + newRefreshToken,
                    username,
                    tokenProvider.getRefreshTokenValidityInMilliseconds(),
                    TimeUnit.MILLISECONDS);

            // RefreshToken 테이블 업데이트
            RefreshToken refreshTokenEntity = refreshTokenRepository.findByUserNo(user.getUserNo())
                    .orElse(RefreshToken.builder().user(user).build());
            refreshTokenEntity.setRefreshToken(newRefreshToken);
            refreshTokenRepository.save(refreshTokenEntity);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getTokenValidityInMilliseconds())
                    .userProfile(createUserProfile(user))
                    .isSuccess(true)
                    .build();

        } catch (Exception e) {
            log.error("❌ 토큰 갱신 실패: {}", e.getMessage());
            throw e;
        }
    }

    // RefreshToken 저장 메서드
    private void saveRefreshToken(User user, String refreshToken) {
        try {
            // Redis 저장
            redisTemplate.opsForValue().set(
                "RT:" + refreshToken,
                user.getUserNo().toString(),
                tokenProvider.getRefreshTokenValidityInMilliseconds(),
                TimeUnit.MILLISECONDS);

            // DB 저장
            RefreshToken refreshTokenEntity = refreshTokenRepository.findByUserNo(user.getUserNo())
                    .orElse(RefreshToken.builder().user(user).build());
            refreshTokenEntity.setRefreshToken(refreshToken);
            refreshTokenRepository.save(refreshTokenEntity);

        } catch (Exception e) {
            log.error("RefreshToken 저장 실패: {}", e.getMessage());
        }
    }

    // UserProfile 생성 메서드
    private UserProfile createUserProfile(User user) {
        return UserProfile.builder()
                .id(user.getUserNo())
                .email(user.getEmail())
                .name(user.getUsername())
                .build();
    }

    // 솔트 생성 함수
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
}