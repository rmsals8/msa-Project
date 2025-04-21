package com.example.auth_service.service;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.oauth2.KakaoUserInfo;
import com.example.auth_service.dto.oauth2.NaverUserInfo;
import com.example.auth_service.dto.request.social.SocialLoginRequest;
import com.example.auth_service.dto.response.auth.AuthResponse;
import com.example.auth_service.dto.response.auth.UserProfile;
import com.example.auth_service.domain.SocialLogin;
import com.example.auth_service.domain.Log;
import com.example.auth_service.domain.RefreshToken;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.repository.SocialLoginRepository;
import com.example.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.repository.LogRepository;
import com.example.auth_service.security.JwtTokenProvider;
import com.example.auth_service.service.oauth.KakaoOAuth2Service;
import com.example.auth_service.service.oauth.NaverOAuth2Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SocialLoginService {
    private final UserRepository userRepository;
    private final SocialLoginRepository socialLoginRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LogRepository logRepository;
    private final JwtTokenProvider tokenProvider;
    private final KakaoOAuth2Service kakaoService;
    private final NaverOAuth2Service naverService;
    private final RedisTemplate<String, String> redisTemplate;

    // 소셜 로그인 코드
    private static final int NAVER_SOCIAL_CODE = 5;
    private static final int KAKAO_SOCIAL_CODE = 4;

    public AuthResponse loginWithNaver(SocialLoginRequest request) {
        try {
            // 네이버 사용자 정보 조회
            NaverUserInfo userInfo = naverService.getUserInfo(request.getAccessToken());
            String naverUserId = userInfo.getId();

            // 1. 소셜 로그인 정보로 기존 사용자 찾기
            Optional<SocialLogin> existingSocialLogin = socialLoginRepository.findByExternalIdAndSocialCode(
                    naverUserId, NAVER_SOCIAL_CODE);

            User user;
            boolean isNewUser = false;

            if (existingSocialLogin.isPresent()) {
                // 기존 소셜 로그인 사용자가 있으면 소셜 정보 업데이트
                SocialLogin socialLogin = existingSocialLogin.get();
                socialLogin.setAccessToken(request.getAccessToken());
                socialLogin.setUpdateDate(LocalDateTime.now());
                socialLoginRepository.save(socialLogin);

                // 사용자 정보 조회
                Optional<User> existingUser = userRepository.findById(socialLogin.getUserNo());
                if (existingUser.isPresent()) {
                    user = existingUser.get();
                } else {
                    // 소셜 로그인 정보는 있지만 사용자 정보가 없는 경우
                    throw new RuntimeException("Social login exists but user not found");
                }
            } else {
                // 2. 기존 소셜 로그인 정보가 없으면, 이메일로 사용자 검색
                Optional<User> existingUser = userRepository.findByEmail(userInfo.getEmail());

                if (existingUser.isPresent()) {
                    // 이미 해당 이메일로 가입한 사용자가 있으면 소셜 로그인 정보만 추가
                    user = existingUser.get();
                } else {
                    // 신규 사용자 생성
                    user = User.builder()
                            .userName(userInfo.getName())
                            .email(userInfo.getEmail())
                            .loginType(1) // 소셜 로그인
                            .build();

                    userRepository.save(user);
                    isNewUser = true;
                }

                // 3. 소셜 로그인 정보 저장
                SocialLogin socialLogin = SocialLogin.builder()
                        .userNo(user.getUserNo())
                        .socialCode(NAVER_SOCIAL_CODE)
                        .externalId(naverUserId)
                        .accessToken(request.getAccessToken())
                        .updateDate(LocalDateTime.now())
                        .build();

                socialLoginRepository.save(socialLogin);
            }

            // 4. 토큰 생성
            String accessToken = tokenProvider.createToken(user.getEmail());
            String refreshToken = tokenProvider.createRefreshToken(user.getEmail());

            // 5. RefreshToken 저장
            saveRefreshToken(user.getUserNo(), refreshToken);

            // 6. 로그인 성공 로그 기록
            saveLog(user.getUserNo(), "NAVER_LOGIN_SUCCESS",
                    "네이버 로그인 성공: " + naverUserId + (isNewUser ? " (신규 가입)" : ""),
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
            log.error("Naver login failed", e);
            // 로그인 실패 로그 기록
            saveLog(null, "NAVER_LOGIN_FAIL",
                    "네이버 로그인 실패: " + e.getMessage(),
                    "127.0.0.1", "Unknown");
            throw new OAuth2AuthenticationException(null, "Failed to process Naver login", e);
        }
    }

    public AuthResponse loginWithKakao(SocialLoginRequest request) {
        try {
            // 카카오 사용자 정보 조회
            KakaoUserInfo userInfo = kakaoService.getUserInfo(request.getAccessToken());
            String kakaoUserId = userInfo.getId();
            String email = userInfo.getEmail() != null ? userInfo.getEmail() : kakaoUserId + "@kakao.com";

            // 1. 소셜 로그인 정보로 기존 사용자 찾기
            Optional<SocialLogin> existingSocialLogin = socialLoginRepository.findByExternalIdAndSocialCode(
                    kakaoUserId, KAKAO_SOCIAL_CODE);

            User user;
            boolean isNewUser = false;

            if (existingSocialLogin.isPresent()) {
                // 기존 소셜 로그인 사용자가 있으면 소셜 정보 업데이트
                SocialLogin socialLogin = existingSocialLogin.get();
                socialLogin.setAccessToken(request.getAccessToken());
                socialLogin.setUpdateDate(LocalDateTime.now());
                socialLoginRepository.save(socialLogin);

                // 사용자 정보 조회
                Optional<User> existingUser = userRepository.findById(socialLogin.getUserNo());
                if (existingUser.isPresent()) {
                    user = existingUser.get();
                } else {
                    // 소셜 로그인 정보는 있지만 사용자 정보가 없는 경우
                    throw new RuntimeException("Social login exists but user not found");
                }
            } else {
                // 2. 기존 소셜 로그인 정보가 없으면, 이메일로 사용자 검색
                Optional<User> existingUser = userRepository.findByEmail(email);

                if (existingUser.isPresent()) {
                    // 이미 해당 이메일로 가입한 사용자가 있으면 소셜 로그인 정보만 추가
                    user = existingUser.get();
                } else {
                    // 신규 사용자 생성
                    user = User.builder()
                            .userName(userInfo.getName())
                            .email(email)
                            .loginType(1) // 소셜 로그인
                            .build();

                    userRepository.save(user);
                    isNewUser = true;
                }

                // 3. 소셜 로그인 정보 저장
                SocialLogin socialLogin = SocialLogin.builder()
                        .userNo(user.getUserNo())
                        .socialCode(KAKAO_SOCIAL_CODE)
                        .externalId(kakaoUserId)
                        .accessToken(request.getAccessToken())
                        .updateDate(LocalDateTime.now())
                        .build();

                socialLoginRepository.save(socialLogin);
            }

            // 4. 토큰 생성
            String accessToken = tokenProvider.createToken(user.getEmail());
            String refreshToken = tokenProvider.createRefreshToken(user.getEmail());

            // 5. RefreshToken 저장
            saveRefreshToken(user.getUserNo(), refreshToken);

            // 6. 로그인 성공 로그 기록
            saveLog(user.getUserNo(), "KAKAO_LOGIN_SUCCESS",
                    "카카오 로그인 성공: " + kakaoUserId + (isNewUser ? " (신규 가입)" : ""),
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
            log.error("Kakao login failed", e);
            // 로그인 실패 로그 기록
            saveLog(null, "KAKAO_LOGIN_FAIL",
                    "카카오 로그인 실패: " + e.getMessage(),
                    "127.0.0.1", "Unknown");
            throw new OAuth2AuthenticationException(null, "Failed to process Kakao login", e);
        }
    }

    // RefreshToken 저장
    private void saveRefreshToken(Long userNo, String refreshToken) {
        // Redis에 저장
        redisTemplate.opsForValue().set(
                "RT:" + refreshToken,
                userNo.toString(),
                tokenProvider.getRefreshTokenValidityInMilliseconds(),
                TimeUnit.MILLISECONDS);

        // DB에도 저장
        RefreshToken refreshTokenEntity = refreshTokenRepository.findByUserNo(userNo)
                .orElse(RefreshToken.builder()
                        .userNo(userNo)
                        .build());

        refreshTokenEntity.setRefreshToken(refreshToken);
        refreshTokenRepository.save(refreshTokenEntity);
    }

    // UserProfile 생성 메서드
    private UserProfile createUserProfile(User user) {
        return UserProfile.builder()
                .id(user.getUserNo())
                .email(user.getEmail())
                .name(user.getUsername())
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