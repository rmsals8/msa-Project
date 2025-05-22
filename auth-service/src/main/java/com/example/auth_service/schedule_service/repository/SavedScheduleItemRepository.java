// SavedScheduleItemRepository.java
package com.example.auth_service.schedule_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.auth_service.schedule_service.domain.SavedSchedule;
import com.example.auth_service.schedule_service.domain.SavedScheduleItem;

import java.util.List;

@Repository
public interface SavedScheduleItemRepository extends JpaRepository<SavedScheduleItem, Long> {
    List<SavedScheduleItem> findBySavedScheduleIdOrderBySequenceNo(Long savedScheduleId);

    @Modifying
    @Query("DELETE FROM SavedScheduleItem i WHERE i.savedSchedule = :schedule")
    void deleteAllBySavedSchedule(@Param("schedule") SavedSchedule schedule);
}