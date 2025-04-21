package com.example.auth_service.service.oauth;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.oauth2.KakaoUserInfo;
import com.example.auth_service.domain.SocialLogin;
import com.example.auth_service.domain.Log;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.repository.SocialLoginRepository;
import com.example.auth_service.repository.LogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuth2Service {
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final SocialLoginRepository socialLoginRepository;
    private final LogRepository logRepository;

    // 카카오 소셜 로그인 코드
    private static final int KAKAO_SOCIAL_CODE = 4;

    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String REDIRECT_URI;

    @Value("${app.api.kakao}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.kakao.client-secret}")
    private String clientSecret;

    public String getAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", REDIRECT_URI);
        params.add("code", code);
        params.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    KAKAO_TOKEN_URL,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Kakao token response: {}", response.getBody());
                return response.getBody().get("access_token").toString();
            } else {
                throw new RuntimeException("Failed to get access token from Kakao");
            }
        } catch (Exception e) {
            log.error("Error getting Kakao access token", e);
            throw new RuntimeException("Failed to get Kakao access token", e);
        }
    }

    public User getOrCreateUser(String accessToken) {
        KakaoUserInfo kakaoUserInfo = getUserInfo(accessToken);
        String kakaoUserId = kakaoUserInfo.getId();
        String email = kakaoUserInfo.getEmail() != null ? kakaoUserInfo.getEmail() : kakaoUserId + "@kakao.com";

        // 1. 소셜 로그인 정보로 기존 사용자 찾기
        Optional<SocialLogin> existingSocialLogin = socialLoginRepository.findByExternalIdAndSocialCode(
                kakaoUserId, KAKAO_SOCIAL_CODE);

        if (existingSocialLogin.isPresent()) {
            // 기존 소셜 로그인 사용자가 있으면 소셜 정보 업데이트
            SocialLogin socialLogin = existingSocialLogin.get();
            socialLogin.setAccessToken(accessToken);
            socialLogin.setUpdateDate(LocalDateTime.now());
            socialLoginRepository.save(socialLogin);

            // 사용자 정보 조회
            Optional<User> user = userRepository.findById(socialLogin.getUserNo());
            if (user.isPresent()) {
                // 로그인 성공 로그 기록
                saveLog(user.get().getUserNo(), "KAKAO_LOGIN_SUCCESS",
                        "카카오 로그인 성공: " + kakaoUserId, "127.0.0.1", "Unknown");
                return user.get();
            }
        }

        // 2. 기존 카카오 로그인 정보가 없으면, 이메일로 사용자 검색
        Optional<User> existingUser = userRepository.findByEmail(email);
        User user;

        if (existingUser.isPresent()) {
            // 이미 해당 이메일로 가입한 사용자가 있으면 소셜 로그인 정보만 추가
            user = existingUser.get();
        } else {
            // 신규 사용자 생성
            user = User.builder()
                    .userName(kakaoUserInfo.getName())
                    .email(email)
                    .loginType(1) // 소셜 로그인
                    .build();

            userRepository.save(user);
        }

        // 3. 소셜 로그인 정보 저장
        SocialLogin socialLogin = SocialLogin.builder()
                .userNo(user.getUserNo())
                .socialCode(KAKAO_SOCIAL_CODE)
                .externalId(kakaoUserId)
                .accessToken(accessToken)
                .updateDate(LocalDateTime.now())
                .build();

        socialLoginRepository.save(socialLogin);

        // 4. 로그인 성공 로그 기록
        saveLog(user.getUserNo(), "KAKAO_LOGIN_SUCCESS",
                "카카오 로그인 성공: " + kakaoUserId, "127.0.0.1", "Unknown");

        return user;
    }

    public KakaoUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    KAKAO_USER_INFO_URL,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return KakaoUserInfo.from(response.getBody());
            } else {
                throw new OAuth2AuthenticationException("Failed to get Kakao user info");
            }
        } catch (Exception e) {
            log.error("Error getting Kakao user info", e);
            throw new OAuth2AuthenticationException(null, "Failed to get Kakao user info", e);
        }
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