package com.example.TripSpring.payload.oauth2;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoUserInfo {
    private String id;
    private String email;
    private String name;
    private String profileImageUrl;
    private String phoneNumber;

    public static KakaoUserInfo from(Map<String, Object> attributes) {
        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");

        if (kakaoAccount == null) {
            throw new IllegalArgumentException("카카오 사용자 정보에 'kakao_account' 필드가 없습니다.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        if (profile == null) {
            throw new IllegalArgumentException("카카오 사용자 정보에 'profile' 필드가 없습니다.");
        }

        return KakaoUserInfo.builder()
                .id(String.valueOf(attributes.get("id")))
                .email((String) kakaoAccount.get("email"))
                .name((String) profile.get("nickname"))
                .profileImageUrl((String) profile.get("profile_image_url"))
                .phoneNumber((String) kakaoAccount.get("phone_number"))
                .build();
    }
}