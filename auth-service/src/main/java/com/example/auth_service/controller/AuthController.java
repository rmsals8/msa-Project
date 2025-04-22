package com.example.auth_service.controller;

import com.example.auth_service.service.SocialLoginService;
import com.example.common.response.ApiResponse;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.request.auth.CompleteSignupRequest;
import com.example.auth_service.dto.request.auth.EmailVerificationRequest;
import com.example.auth_service.dto.request.auth.LoginRequest;
import com.example.auth_service.dto.request.auth.TokenRefreshRequest;
import com.example.auth_service.dto.request.auth.VerifyEmailRequest;
import com.example.auth_service.dto.request.social.SocialLoginRequest;
import com.example.auth_service.dto.response.auth.AuthResponse;
import com.example.auth_service.dto.response.auth.MessageResponse;
import com.example.auth_service.dto.response.auth.VerificationResponse;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.service.AuthService;
import com.example.auth_service.service.EmailVerificationService;
import com.example.auth_service.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    // 새 의존성 추가
    private final EmailVerificationService emailVerificationService;

    // auth-service/src/main/java/com/example/auth_service/controller/AuthController.java에
    // 추가
    // auth-service/src/main/java/com/example/auth_service/controller/AuthController.java

    @GetMapping("/validate-token")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String bearerToken) {
        try {
            if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "토큰이 없거나 잘못된 형식입니다."));
            }

            String token = bearerToken.substring(7);

            if (!tokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "유효하지 않은 토큰입니다."));
            }

            String email = tokenProvider.getUsername(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("userNo", user.getUserNo());
            response.put("email", email);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("토큰 검증 실패", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "토큰 검증 실패: " + e.getMessage()));
        }
    }
    // 기존 메서드들 유지

    // 이메일 인증 요청
    @PostMapping("/email-verify-request")
    public ResponseEntity<ApiResponse<MessageResponse>> requestEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request) {
        log.info("이메일 인증 요청: email={}", request.getEmail());

        emailVerificationService.sendVerificationCode(request.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "인증번호가 발송되었습니다. 이메일을 확인해주세요.",
                new MessageResponse("인증번호가 발송되었습니다.")));
    }

    // 이메일 인증 코드 확인
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<VerificationResponse>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        log.info("이메일 인증 코드 확인: email={}, code={}",
                request.getEmail(), request.getVerificationCode());

        boolean isValid = emailVerificationService.verifyCode(
                request.getEmail(), request.getVerificationCode());

        if (isValid) {
            String verificationToken = emailVerificationService.generateVerificationToken(request.getEmail());

            return ResponseEntity.ok(ApiResponse.success(
                    "이메일 인증이 완료되었습니다. 회원가입을 진행해주세요.",
                    new VerificationResponse(true, verificationToken)));
        } else {
            return ResponseEntity.ok(ApiResponse.error(
                    HttpStatus.BAD_REQUEST,
                    "인증번호가 일치하지 않거나 만료되었습니다.",
                    new VerificationResponse(false, null)));
        }
    }

    // 이메일 인증 후 최종 회원가입
    @PostMapping("/complete-signup")
    public ResponseEntity<AuthResponse> completeSignup(
            @Valid @RequestBody CompleteSignupRequest request) {
        log.info("회원가입 완료 요청: email={}", request.getEmail());

        return ResponseEntity.ok(
                authService.completeSignup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/social/kakao")
    public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialLoginService.loginWithKakao(request));
    }

    @PostMapping("/social/naver")
    public ResponseEntity<AuthResponse> naverLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialLoginService.loginWithNaver(request));
    }
}