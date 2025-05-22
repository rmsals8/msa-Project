package com.example.auth_service.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.auth_service.domain.Password;
import com.example.auth_service.auth_service.domain.User;

public interface PasswordRepository extends JpaRepository<Password, Long> {
    // userNo 대신 user를 기준으로 찾는 메서드
    Optional<Password> findByUser(User user);

    Optional<Password> findByUser_UserNo(Long userNo);
}