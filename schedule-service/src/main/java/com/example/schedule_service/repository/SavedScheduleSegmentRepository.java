// SavedScheduleSegmentRepository.java
package com.example.schedule_service.repository;

import com.example.schedule_service.domain.SavedSchedule;
import com.example.schedule_service.domain.SavedScheduleSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedScheduleSegmentRepository extends JpaRepository<SavedScheduleSegment, Long> {
    List<SavedScheduleSegment> findBySavedScheduleId(Long savedScheduleId);

    @Modifying
    @Query("DELETE FROM SavedScheduleSegment s WHERE s.savedSchedule = :schedule")
    void deleteAllBySavedSchedule(@Param("schedule") SavedSchedule schedule);
}