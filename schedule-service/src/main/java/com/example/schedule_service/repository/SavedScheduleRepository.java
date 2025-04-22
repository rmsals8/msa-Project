// SavedScheduleRepository.java
package com.example.schedule_service.repository;

import com.example.schedule_service.domain.SavedSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SavedScheduleRepository extends JpaRepository<SavedSchedule, Long> {
    // 사용자의 활성화된 저장 일정 목록 조회
    @Query("SELECT s FROM SavedSchedule s WHERE s.userNo = :userNo AND s.isDeleted = false AND s.expirationDate > :now")
    List<SavedSchedule> findActiveByUserNo(@Param("userNo") Long userNo, @Param("now") LocalDateTime now);
    
    // 사용자의 저장된 일정 개수 조회
    @Query("SELECT COUNT(s) FROM SavedSchedule s WHERE s.userNo = :userNo AND s.isDeleted = false AND s.expirationDate > :now")
    int countActiveByUserNo(@Param("userNo") Long userNo, @Param("now") LocalDateTime now);
    
    // 특정 일정 상세 조회
    @Query("SELECT s FROM SavedSchedule s LEFT JOIN FETCH s.scheduleItems LEFT JOIN FETCH s.segments WHERE s.id = :id AND s.userNo = :userNo AND s.isDeleted = false")
    Optional<SavedSchedule> findByIdAndUserNo(@Param("id") Long id, @Param("userNo") Long userNo);
    
    // 만료된 일정 조회
    @Query("SELECT s FROM SavedSchedule s WHERE s.expirationDate <= :now AND s.isDeleted = false")
    List<SavedSchedule> findExpiredSchedules(@Param("now") LocalDateTime now);
}
