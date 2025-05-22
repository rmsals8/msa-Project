package com.example.auth_service.place_service.service;

import com.example.auth_service.place_service.domain.UserPreference;
import com.example.auth_service.place_service.dto.UserPreferenceDto;
import com.example.auth_service.place_service.repository.UserPreferenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final ObjectMapper objectMapper;

    /**
     * 사용자의 카테고리 선호도 저장
     * 이미 존재하면 업데이트, 없으면 새로 생성
     */
    @Transactional
    public void savePreferences(String userId, UserPreferenceDto preferenceDto) {
        try {
            // 카테고리 목록을 JSON 문자열로 변환
            String categoriesJson = objectMapper.writeValueAsString(preferenceDto.getCategories());

            // 사용자 선호도 조회
            UserPreference userPreference = userPreferenceRepository.findByUserId(userId)
                    .orElse(UserPreference.builder().userId(userId).build());

            // 카테고리 업데이트
            userPreference.setCategories(categoriesJson);

            // 저장
            userPreferenceRepository.save(userPreference);

            log.info("카테고리 선호도 저장 성공: userId={}, categories={}", userId, preferenceDto.getCategories());
        } catch (JsonProcessingException e) {
            log.error("카테고리 선호도 JSON 변환 오류: {}", e.getMessage(), e);
            throw new RuntimeException("카테고리 선호도 저장 실패", e);
        }
    }

    /**
     * 사용자의 카테고리 선호도 조회
     */
    @Transactional(readOnly = true)
    public UserPreferenceDto getPreferences(String userId) {
        try {
            // 사용자 선호도 조회
            UserPreference userPreference = userPreferenceRepository.findByUserId(userId)
                    .orElse(null);

            // 선호도가 없으면 빈 목록 반환
            if (userPreference == null || userPreference.getCategories() == null) {
                return new UserPreferenceDto(new ArrayList<>());
            }

            // JSON 문자열을 카테고리 목록으로 변환
            List<String> categories = objectMapper.readValue(
                    userPreference.getCategories(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            return new UserPreferenceDto(categories);
        } catch (JsonProcessingException e) {
            log.error("카테고리 선호도 JSON 파싱 오류: {}", e.getMessage(), e);
            return new UserPreferenceDto(new ArrayList<>());
        }
    }

    /**
     * 사용자의 카테고리 선호도 삭제
     */
    @Transactional
    public void deletePreferences(String userId) {
        userPreferenceRepository.deleteByUserId(userId);
        log.info("카테고리 선호도 삭제 완료: userId={}", userId);
    }
}