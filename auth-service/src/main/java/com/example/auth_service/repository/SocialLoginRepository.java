package com.example.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;


import com.example.auth_service.domain.SocialLogin;
import com.example.auth_service.domain.User;

public interface SocialLoginRepository extends JpaRepository<SocialLogin, Long> {
    Optional<SocialLogin> findByExternalIdAndSocialCode(String externalId, Integer socialCode);
    
    Optional<SocialLogin> findByUserAndSocialCode(User user, Integer socialCode);
    
    // 호환성을 위한 쿼리 메서드
    Optional<SocialLogin> findByUser_UserNoAndSocialCode(Long userNo, Integer socialCode);
}