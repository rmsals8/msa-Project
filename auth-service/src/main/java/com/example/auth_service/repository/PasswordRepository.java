package com.example.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.Password;

public interface PasswordRepository extends JpaRepository<Password, Long> {
    Optional<Password> findByUserNo(Long userNo);
}
