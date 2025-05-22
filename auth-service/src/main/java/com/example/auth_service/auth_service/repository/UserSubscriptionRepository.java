package com.example.auth_service.auth_service.repository;

import com.example.auth_service.auth_service.domain.UserSubscription;
import com.example.auth_service.auth_service.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUserAndStatus(User user, UserSubscription.SubscriptionStatus status);

    Optional<UserSubscription> findTopByUserOrderByCreatedAtDesc(User user);

    Optional<UserSubscription> findByUser_UserNoAndStatus(Long userNo, UserSubscription.SubscriptionStatus status);
}