package com.example.auth_service.auth_service.service;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.dto.oauth2.KakaoUserInfo;
import com.example.auth_service.auth_service.dto.oauth2.NaverUserInfo;
import com.example.auth_service.auth_service.dto.request.social.SocialLoginRequest;
import com.example.auth_service.auth_service.dto.response.auth.AuthResponse;
import com.example.auth_service.auth_service.dto.response.auth.UserProfile;
import com.example.auth_service.auth_service.domain.SocialLogin;
import com.example.auth_service.auth_service.domain.Log;
import com.example.auth_service.auth_service.domain.RefreshToken;
import com.example.auth_service.auth_service.repository.UserRepository;
import com.example.auth_service.auth_service.repository.SocialLoginRepository;
import com.example.auth_service.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.auth_service.repository.LogRepository;
import com.example.auth_service.auth_service.security.JwtTokenProvider;
import com.example.auth_service.auth_service.service.oauth.KakaoOAuth2Service;
import com.example.auth_service.auth_service.service.oauth.NaverOAuth2Service;

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
    // ✅ description 최대 길이 제한 (DB 컬럼 크기에 맞춤)
    private static final int MAX_DESCRIPTION_LENGTH = 255;

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
                user = socialLogin.getUser();
                if (user == null) {
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
                        .user(user) // userNo 대신 user 객체 전달
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
            saveRefreshToken(user, refreshToken);

            // 6. 로그인 성공 로그 기록 (✅ description 길이 제한)
            String logDescription = "네이버 로그인 성공: " + naverUserId + (isNewUser ? " (신규 가입)" : "");
            saveLog(user, "NAVER_LOGIN_SUCCESS", logDescription, "127.0.0.1", "Unknown");

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
            // 로그인 실패 로그 기록 (✅ description 길이 제한)
            String logDescription = "네이버 로그인 실패: " + e.getMessage();
            saveLog(null, "NAVER_LOGIN_FAIL", logDescription, "127.0.0.1", "Unknown");
            throw new RuntimeException("Failed to process Naver login", e);
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
                user = socialLogin.getUser();
                if (user == null) {
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
                        .user(user) // userNo 대신 user 객체 전달
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
            saveRefreshToken(user, refreshToken);

            // 6. 로그인 성공 로그 기록 (✅ description 길이 제한)
            String logDescription = "카카오 로그인 성공: " + kakaoUserId + (isNewUser ? " (신규 가입)" : "");
            saveLog(user, "KAKAO_LOGIN_SUCCESS", logDescription, "127.0.0.1", "Unknown");

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
            // 로그인 실패 로그 기록 (✅ description 길이 제한)
            String logDescription = "카카오 로그인 실패: " + e.getMessage();
            saveLog(null, "KAKAO_LOGIN_FAIL", logDescription, "127.0.0.1", "Unknown");
            throw new RuntimeException("Failed to process Kakao login", e);
        }
    }

    // RefreshToken 저장
    private void saveRefreshToken(User user, String refreshToken) {
        // Redis에 저장
        redisTemplate.opsForValue().set(
                "RT:" + refreshToken,
                user.getUserNo().toString(),
                tokenProvider.getRefreshTokenValidityInMilliseconds(),
                TimeUnit.MILLISECONDS);

        // DB에도 저장
        RefreshToken refreshTokenEntity = refreshTokenRepository.findByUserNo(user.getUserNo())
                .orElse(RefreshToken.builder()
                        .user(user)
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

    // ✅ 안전한 로그 저장 메서드 (description 길이 제한)
    private void saveLog(User user, String actionType, String description, String ipAddress, String userAgent) {
        try {
            // description이 너무 길면 자르기 (DB 제약사항 준수)
            String safeDescription = description;
            if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
                safeDescription = description.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
                log.warn("로그 설명이 너무 길어서 잘림: 원본길이={}, 잘린길이={}", 
                         description.length(), safeDescription.length());
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
            log.debug("로그 저장 완료: actionType={}, user={}", actionType, user != null ? user.getUserNo() : "null");

        } catch (Exception e) {
            // 로그 저장 실패는 메인 로직에 영향을 주면 안 되므로 에러만 기록
            log.error("로그 저장 실패: actionType={}, error={}", actionType, e.getMessage());
        }
    }
}