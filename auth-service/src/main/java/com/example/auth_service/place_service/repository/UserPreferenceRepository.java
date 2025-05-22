package com.example.auth_service.place_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.auth_service.place_service.domain.UserPreference;

import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /**
     * 사용자 ID로 선호도 정보 조회
     */
    Optional<UserPreference> findByUserId(String userId);

    /**
     * 사용자 ID로 선호도 정보 삭제
     */
    void deleteByUserId(String userId);
}