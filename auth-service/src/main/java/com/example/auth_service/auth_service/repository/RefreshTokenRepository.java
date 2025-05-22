package com.example.auth_service.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.auth_service.auth_service.domain.RefreshToken;
import com.example.auth_service.auth_service.domain.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByUser_UserNo(Long userNo);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    void deleteByUser(User user);

    // 호환성을 위한 쿼리 메서드
    @Query("SELECT r FROM RefreshToken r WHERE r.user.userNo = :userNo")
    Optional<RefreshToken> findByUserNo(@Param("userNo") Long userNo);

    @Query("DELETE FROM RefreshToken r WHERE r.user.userNo = :userNo")
    void deleteByUser_UserNo(Long userNo);
}