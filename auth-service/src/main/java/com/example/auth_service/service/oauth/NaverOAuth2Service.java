package com.example.auth_service.service.oauth;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.oauth2.NaverUserInfo;
import com.example.auth_service.domain.SocialLogin;
import com.example.auth_service.domain.Log;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.repository.SocialLoginRepository;
import com.example.auth_service.repository.LogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
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
public class NaverOAuth2Service {
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final SocialLoginRepository socialLoginRepository;
    private final LogRepository logRepository;

    // 네이버 소셜 로그인 코드
    private static final int NAVER_SOCIAL_CODE = 5;

    @Value("${app.api.naver.client-id2}")
    private String clientId;

    @Value("${app.api.naver.client-secret2}")
    private String clientSecret;
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String NAVER_API_URL = "https://openapi.naver.com/v1/nid/me";

    public NaverUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    NAVER_API_URL,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return NaverUserInfo.from(response.getBody());
            } else {
                throw new OAuth2AuthenticationException("Failed to get Naver user info");
            }
        } catch (Exception e) {
            log.error("Error getting Naver user info", e);
            throw new OAuth2AuthenticationException(null, "Failed to get Naver user info", e);
        }
    }

    public String getAccessToken(String code, String state) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("code", code);
            body.add("state", state);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.info("Requesting Naver access token with code: {}", code);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    TOKEN_URL,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            log.info("Naver token response: {}", response.getBody());

            if (response.getBody() != null && response.getBody().get("access_token") != null) {
                return response.getBody().get("access_token").toString();
            }

            throw new OAuth2AuthenticationException("Failed to get access token from Naver");
        } catch (Exception e) {
            log.error("Error getting Naver access token", e);
            throw new OAuth2AuthenticationException(null, "Failed to get Naver access token: " + e.getMessage(), e);
        }
    }

    public User getOrCreateUser(String accessToken) {
        NaverUserInfo userInfo = getUserInfo(accessToken);
        String naverUserId = userInfo.getId();

        // 1. 소셜 로그인 정보로 기존 사용자 찾기
        Optional<SocialLogin> existingSocialLogin = socialLoginRepository.findByExternalIdAndSocialCode(
                naverUserId, NAVER_SOCIAL_CODE);

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
                saveLog(user.get().getUserNo(), "NAVER_LOGIN_SUCCESS",
                        "네이버 로그인 성공: " + naverUserId, "127.0.0.1", "Unknown");
                return user.get();
            }
        }

        // 2. 기존 네이버 로그인 정보가 없으면, 이메일로 사용자 검색
        Optional<User> existingUser = userRepository.findByEmail(userInfo.getEmail());
        User user;

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
        }

        // 3. 소셜 로그인 정보 저장
        SocialLogin socialLogin = SocialLogin.builder()
                .userNo(user.getUserNo())
                .socialCode(NAVER_SOCIAL_CODE)
                .externalId(naverUserId)
                .accessToken(accessToken)
                .updateDate(LocalDateTime.now())
                .build();

        socialLoginRepository.save(socialLogin);

        // 4. 로그인 성공 로그 기록
        saveLog(user.getUserNo(), "NAVER_LOGIN_SUCCESS",
                "네이버 로그인 성공: " + naverUserId, "127.0.0.1", "Unknown");

        return user;
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