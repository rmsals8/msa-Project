// SavedScheduleItemRepository.java
package com.example.schedule_service.repository;

import com.example.schedule_service.domain.SavedSchedule;
import com.example.schedule_service.domain.SavedScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedScheduleItemRepository extends JpaRepository<SavedScheduleItem, Long> {
    List<SavedScheduleItem> findBySavedScheduleIdOrderBySequenceNo(Long savedScheduleId);

    @Modifying
    @Query("DELETE FROM SavedScheduleItem i WHERE i.savedSchedule = :schedule")
    void deleteAllBySavedSchedule(@Param("schedule") SavedSchedule schedule);
}