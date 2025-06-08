package com.example.auth_service.auth_service.repository;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.UserSubscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserName(String userName);

    // ✅ 기본 이메일 조회 (가장 빠름)
    Optional<User> findByEmail(String email);

    // ✅ 성능 최적화: 로그인용 간단한 쿼리
    @Query("SELECT u FROM User u " +
           "LEFT JOIN FETCH u.password " +
           "WHERE u.email = :email")
    Optional<User> findByEmailForLogin(@Param("email") String email);

    // ✅ 기존 복잡한 쿼리는 필요할 때만 사용
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.password " +
            "LEFT JOIN FETCH u.userAgreement " +
            "WHERE u.email = :email")
    Optional<User> findByEmailWithDetails(@Param("email") String email);

    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.subscriptions s " +
            "LEFT JOIN FETCH u.dailyUsages d " +
            "WHERE u.userNo = :userNo " +
            "AND (s.status = :status OR s IS NULL) " +
            "AND (d.usageDate = :today OR d IS NULL)")
    Optional<User> findByIdWithUsageInfo(@Param("userNo") Long userNo,
            @Param("status") UserSubscription.SubscriptionStatus status,
            @Param("today") LocalDate today);

    boolean existsByEmail(String email);
}