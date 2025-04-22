// SavedScheduleItemRepository.java
package com.example.schedule_service.repository;

import com.example.schedule_service.domain.SavedScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedScheduleItemRepository extends JpaRepository<SavedScheduleItem, Long> {
    List<SavedScheduleItem> findBySavedScheduleIdOrderBySequenceNo(Long savedScheduleId);
}