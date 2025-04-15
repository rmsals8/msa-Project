package com.example.auth_service.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 인증 응답 DTO
@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserProfile userProfile;
    private boolean isSuccess; // 추가된 필드
}
