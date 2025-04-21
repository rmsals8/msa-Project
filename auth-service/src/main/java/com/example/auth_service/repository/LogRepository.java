package com.example.auth_service.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.Log;

public interface LogRepository extends JpaRepository<Log, Long> {
    List<Log> findByUser_UserNo(Long userNo);

    List<Log> findByActionType(String actionType);

    List<Log> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}