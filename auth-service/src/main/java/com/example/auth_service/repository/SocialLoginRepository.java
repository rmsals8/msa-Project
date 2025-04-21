package com.example.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.SocialLogin;

public interface SocialLoginRepository extends JpaRepository<SocialLogin, Long> {
    Optional<SocialLogin> findByExternalIdAndSocialCode(String externalId, Integer socialCode);

    Optional<SocialLogin> findByUserNoAndSocialCode(Long userNo, Integer socialCode);
}