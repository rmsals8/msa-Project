package com.example.auth_service.auth_service.repository;

import com.example.auth_service.auth_service.domain.DailyUsage;
import com.example.auth_service.auth_service.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyUsageRepository extends JpaRepository<DailyUsage, Long> {

    Optional<DailyUsage> findByUserAndUsageDate(User user, LocalDate usageDate);

    Optional<DailyUsage> findByUser_UserNoAndUsageDate(Long userNo, LocalDate usageDate);
}