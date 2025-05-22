package com.example.auth_service.auth_service.repository;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.domain.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {
    Optional<UserAgreement> findByUser(User user);
}