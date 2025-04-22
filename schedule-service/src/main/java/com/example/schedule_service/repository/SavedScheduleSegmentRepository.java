// SavedScheduleSegmentRepository.java
package com.example.schedule_service.repository;

import com.example.schedule_service.domain.SavedScheduleSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedScheduleSegmentRepository extends JpaRepository<SavedScheduleSegment, Long> {
    List<SavedScheduleSegment> findBySavedScheduleId(Long savedScheduleId);
}