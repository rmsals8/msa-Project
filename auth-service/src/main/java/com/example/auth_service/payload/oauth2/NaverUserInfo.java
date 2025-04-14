package com.example.TripSpring.payload.oauth2;

import lombok.Builder;
import lombok.Getter;
import java.util.Map;

@Getter
@Builder
public class NaverUserInfo {
    private String id;
    private String email;
    private String name;
    private String profileImageUrl;
    private String phoneNumber;

    public static NaverUserInfo from(Map<String, Object> attributes) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        if (response == null) {
            throw new IllegalArgumentException("네이버 사용자 정보에 'response' 필드가 없습니다.");
        }

        return NaverUserInfo.builder()
                .id((String) response.get("id"))
                .email((String) response.get("email"))
                .name((String) response.get("name"))
                .profileImageUrl((String) response.get("profile_image"))
                .phoneNumber((String) response.get("mobile"))
                .build();
    }
}