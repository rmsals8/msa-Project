package com.example.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByUserNo(Long userNo);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    void deleteByUserNo(Long userNo);
}