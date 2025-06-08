package com.example.auth_service.auth_service.repository;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.UserSubscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    
    // ✅ 기본 조회 메서드들 (캐시 적용)
    @Cacheable(value = "users", key = "#userName")
    Optional<User> findByUserName(String userName);

    @Cacheable(value = "users", key = "#email")
    Optional<User> findByEmail(String email);

    // ✅ 성능 최적화: fetch join으로 N+1 쿼리 해결
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.password p " +
            "LEFT JOIN FETCH u.userAgreement ua " +
            "WHERE u.email = :email")
    Optional<User> findByEmailWithDetails(@Param("email") String email);

    // ✅ 사용량 정보까지 한 번에 조회 (복잡한 로직용)
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.subscriptions s " +
            "LEFT JOIN FETCH u.dailyUsages d " +
            "WHERE u.userNo = :userNo " +
            "AND (s.status = :status OR s IS NULL) " +
            "AND (d.usageDate = :today OR d IS NULL)")
    Optional<User> findByIdWithUsageInfo(@Param("userNo") Long userNo,
            @Param("status") UserSubscription.SubscriptionStatus status,
            @Param("today") LocalDate today);

    // ✅ 간단한 존재 여부 확인 (인덱스 활용)
    boolean existsByEmail(String email);

    // ✅ 소셜 로그인 사용자 조회 최적화
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.socialLogins sl " +
            "WHERE u.email = :email AND u.loginType = 1")
    Optional<User> findSocialUserByEmail(@Param("email") String email);

    // ✅ ID로 기본 정보만 빠르게 조회
    @Query("SELECT u.userNo, u.userName, u.email, u.loginType, u.status FROM User u WHERE u.userNo = :userNo")
    Optional<Object[]> findBasicInfoById(@Param("userNo") Long userNo);

    // ✅ 활성 사용자만 조회 (성능 향상)
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.status = 'ACTIVE'")
    Optional<User> findActiveUserByEmail(@Param("email") String email);
}