package com.example.auth_service.controller;

import com.example.auth_service.service.SocialLoginService;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.payload.request.LoginRequest;
import com.example.auth_service.payload.request.SignupRequest;
import com.example.auth_service.payload.request.SocialLoginRequest;
import com.example.auth_service.payload.request.TokenRefreshRequest;
import com.example.auth_service.payload.response.AuthResponse;

import com.example.auth_service.service.AuthService;

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

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
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