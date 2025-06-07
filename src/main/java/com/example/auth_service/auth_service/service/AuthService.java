package com.example.auth_service.auth_service.service;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.UserAgreement;
import com.example.auth_service.auth_service.dto.request.auth.CompleteSignupRequest;
import com.example.auth_service.auth_service.dto.request.auth.LoginRequest;
import com.example.auth_service.auth_service.dto.request.auth.TokenRefreshRequest;
import com.example.auth_service.auth_service.dto.response.auth.AuthResponse;
import com.example.auth_service.auth_service.dto.response.auth.UserProfile;
import com.example.auth_service.auth_service.domain.Password;
import com.example.auth_service.auth_service.domain.Log;
import com.example.auth_service.auth_service.domain.RefreshToken;
import com.example.auth_service.auth_service.exception.BadRequestException;
import com.example.auth_service.auth_service.exception.ResourceNotFoundException;
import com.example.auth_service.auth_service.repository.UserRepository;
import com.example.auth_service.auth_service.repository.PasswordRepository;
import com.example.auth_service.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.auth_service.repository.LogRepository;
import com.example.auth_service.auth_service.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordRepository passwordRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailVerificationService emailVerificationService;

    // ✅ 간단한 회원가입 처리 (병렬 처리 없음)
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

            // 6. 토큰 생성 (순차 처리)
            String accessToken = tokenProvider.createToken(savedUser.getEmail());
            String refreshToken = tokenProvider.createRefreshToken(savedUser.getEmail());

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

    // ✅ 간단한 로그인 처리 (병렬 처리 없음)
    public AuthResponse login(LoginRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("로그인 시작: {}", request.getEmail());

        try {
            // 1. 사용자 존재 여부 확인
            if (!userRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("존재하지 않는 사용자입니다.");
            }

            // 2. 인증 처리
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 3. 사용자 조회
            User user = userRepository.findByEmailWithDetails(request.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

            // 4. 토큰 생성 (순차 처리)
            String accessToken = tokenProvider.createToken(authentication);
            String refreshToken = tokenProvider.createRefreshToken(authentication.getName());

            // 5. RefreshToken 저장
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

    // ✅ 토큰 갱신 처리
    public AuthResponse refresh(TokenRefreshRequest request) {
        try {
            // Redis에서 리프레시 토큰 검증
            String savedToken = redisTemplate.opsForValue().get("RT:" + request.getRefreshToken());
            if (savedToken == null) {
                throw new BadRequestException("Invalid refresh token");
            }

            // 토큰에서 사용자 정보 추출
            String username = tokenProvider.getUsername(request.getRefreshToken());
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", username));

            // 새 토큰 생성 (순차 처리)
            String newAccessToken = tokenProvider.createToken(username);
            String newRefreshToken = tokenProvider.createRefreshToken(username);

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