package com.example.auth_service.service;

import com.example.auth_service.domain.User;
import com.example.auth_service.domain.Password;
import com.example.auth_service.domain.Log;
import com.example.auth_service.domain.RefreshToken;
import com.example.auth_service.exception.BadRequestException;
import com.example.auth_service.exception.ResourceNotFoundException;
import com.example.auth_service.payload.request.LoginRequest;
import com.example.auth_service.payload.request.SignupRequest;
import com.example.auth_service.payload.request.TokenRefreshRequest;
import com.example.auth_service.payload.response.AuthResponse;
import com.example.auth_service.payload.response.UserProfile;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.repository.PasswordRepository;
import com.example.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.repository.LogRepository;
import com.example.auth_service.security.JwtTokenProvider;

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
    private final LogRepository logRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("이미 사용중인 이메일입니다.");
        }

        // 사용자 생성
        User user = User.builder()
                .userName(request.getName())
                .email(request.getEmail())
                .loginType(0) // 일반 로그인은 0
                .build();

        userRepository.save(user);

        // 비밀번호 저장 - 저장된 사용자의 ID를 가져와서 사용
        String salt = generateSalt(); // 솔트 생성 함수 추가
        Password password = Password.builder()
                .userNo(user.getUserNo())
                .salt(salt)
                .password(passwordEncoder.encode(request.getPassword()))
                .updateDate(LocalDateTime.now())
                .build();

        passwordRepository.save(password);

        // 회원가입 로그 기록
        saveLog(user.getUserNo(), "SIGNUP", "회원가입 성공: " + request.getEmail(),
                "127.0.0.1", "Unknown");

        // 중요: 자동 로그인을 시도하지 않고 성공 응답만 반환
        return createAuthResponse(user);
    }

    // 회원가입 성공 후 토큰 생성만 (로그인 시도 없이)
    private AuthResponse createAuthResponse(User user) {
        String accessToken = tokenProvider.createToken(user.getEmail());
        String refreshToken = tokenProvider.createRefreshToken(user.getEmail());

        // RefreshToken 저장
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userNo(user.getUserNo())
                .refreshToken(refreshToken)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        // Redis에도 저장
        redisTemplate.opsForValue().set(
                "RT:" + refreshToken,
                user.getEmail(),
                tokenProvider.getRefreshTokenValidityInMilliseconds(),
                TimeUnit.MILLISECONDS);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getTokenValidityInMilliseconds())
                .userProfile(createUserProfile(user))
                .isSuccess(true)
                .build();
    }

    // 솔트 생성 함수 추가
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public AuthResponse login(LoginRequest request) {
        return authenticateUser(request.getEmail(), request.getPassword());
    }

    public AuthResponse refresh(TokenRefreshRequest request) {
        // Redis에서 리프레시 토큰 검증
        String savedToken = redisTemplate.opsForValue().get("RT:" + request.getRefreshToken());
        if (savedToken == null) {
            throw new BadRequestException("Invalid refresh token");
        }

        // 토큰에서 사용자 정보 추출
        String username = tokenProvider.getUsername(request.getRefreshToken());
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", username));

        // 새 토큰 생성
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
                .orElse(RefreshToken.builder()
                        .userNo(user.getUserNo())
                        .build());

        refreshTokenEntity.setRefreshToken(newRefreshToken);
        refreshTokenRepository.save(refreshTokenEntity);

        // 토큰 갱신 로그 기록
        saveLog(user.getUserNo(), "TOKEN_REFRESH", "토큰 갱신 성공",
                "127.0.0.1", "Unknown");

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getTokenValidityInMilliseconds())
                .userProfile(createUserProfile(user))
                .build();
    }

    public void logout(String accessToken, String refreshToken) {
        try {
            // Access Token 블랙리스트에 추가
            long expiration = tokenProvider.getExpirationFromToken(accessToken);
            redisTemplate.opsForValue().set(
                    "BL:" + accessToken,
                    "logout",
                    expiration,
                    TimeUnit.MILLISECONDS);

            // Refresh Token 삭제
            redisTemplate.delete("RT:" + refreshToken);

            // 사용자 ID 추출
            String username = tokenProvider.getUsername(accessToken);
            User user = userRepository.findByEmail(username).orElse(null);

            if (user != null) {
                // RefreshToken 테이블에서도 삭제
                refreshTokenRepository.deleteByUserNo(user.getUserNo());

                // 로그아웃 로그 기록
                saveLog(user.getUserNo(), "LOGOUT", "로그아웃 성공",
                        "127.0.0.1", "Unknown");
            }
        } catch (Exception e) {
            log.error("로그아웃 처리 중 오류 발생", e);
        }
    }

    private AuthResponse authenticateUser(String email, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

            String accessToken = tokenProvider.createToken(authentication);
            String refreshToken = tokenProvider.createRefreshToken(authentication.getName());

            // Refresh Token을 Redis에 저장
            redisTemplate.opsForValue().set(
                    "RT:" + refreshToken,
                    authentication.getName(),
                    tokenProvider.getRefreshTokenValidityInMilliseconds(),
                    TimeUnit.MILLISECONDS);

            // RefreshToken 테이블에도 저장
            RefreshToken refreshTokenEntity = refreshTokenRepository.findByUserNo(user.getUserNo())
                    .orElse(RefreshToken.builder()
                            .userNo(user.getUserNo())
                            .build());

            refreshTokenEntity.setRefreshToken(refreshToken);
            refreshTokenRepository.save(refreshTokenEntity);

            // 로그인 시간 업데이트는 로그 테이블에 기록으로 대체
            saveLog(user.getUserNo(), "LOGIN_SUCCESS", "로그인 성공: " + email,
                    "127.0.0.1", "Unknown");

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getTokenValidityInMilliseconds())
                    .userProfile(createUserProfile(user))
                    .isSuccess(true)
                    .build();
        } catch (Exception e) {
            log.error("로그인 실패: {}", email, e);
            // 로그인 실패 로그 기록
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                saveLog(user.getUserNo(), "LOGIN_FAIL", "로그인 실패: " + e.getMessage(),
                        "127.0.0.1", "Unknown");
            } else {
                saveLog(null, "LOGIN_FAIL", "존재하지 않는 이메일로 로그인 시도: " + email,
                        "127.0.0.1", "Unknown");
            }
            throw e;
        }
    }

    // UserProfile 생성 메서드
    private UserProfile createUserProfile(User user) {
        return UserProfile.builder()
                .id(user.getUserNo())
                .email(user.getEmail())
                .name(user.getUsername())
                // 전화번호, 프로필 이미지, 제공자, 역할 등은 기존 코드에 있었지만 새 구조에서는 없을 수 있음
                // 필요시 SocialLogin 테이블이나 다른 테이블에서 추가 정보를 가져올 수 있음
                .build();
    }

    // 로그 저장 메서드
    private void saveLog(Long userNo, String actionType, String description, String ipAddress, String userAgent) {
        Log log = Log.builder()
                .userNo(userNo)
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