package com.example.place_service.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.place_service.domain.VisitHistory;
import com.example.place_service.dto.VisitHistoryDto;
import com.example.place_service.repository.VisitHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VisitHistoryService {

    private final VisitHistoryRepository visitHistoryRepository;

    @Transactional
    public VisitHistory addVisitHistory(VisitHistoryDto dto, String userId) {
        Optional<VisitHistory> existingVisit = visitHistoryRepository.findByUserIdAndPlaceId(userId, dto.getPlaceId());

        if (existingVisit.isPresent()) {
            VisitHistory history = existingVisit.get();
            history.setVisitCount(history.getVisitCount() + 1);
            history.setVisitDate(LocalDateTime.now());
            return visitHistoryRepository.save(history);
        } else {
            VisitHistory newVisit = VisitHistory.builder()
                    .userId(userId)
                    .placeName(dto.getPlaceName())
                    .placeId(dto.getPlaceId())
                    .category(dto.getCategory())
                    .latitude(dto.getLatitude())
                    .longitude(dto.getLongitude())
                    .address(dto.getAddress())
                    .visitDate(LocalDateTime.now())
                    .visitCount(1)
                    .build();
            return visitHistoryRepository.save(newVisit);
        }
    }

    // 페이징 처리가 적용된 방문 기록 조회 메서드
    public Page<VisitHistory> getVisitHistoriesPaged(String userId, Pageable pageable) {
        return visitHistoryRepository.findByUserIdOrderByVisitDateDesc(userId, pageable);
    }

    // 페이징 처리가 적용된 카테고리별 방문 기록 조회 메서드
    public Page<VisitHistory> getVisitHistoriesByCategoryPaged(String userId, String category, Pageable pageable) {
        return visitHistoryRepository.findByUserIdAndCategoryPageable(userId, category, pageable);
    }

    // 기존 메서드들 유지
    public List<VisitHistory> getVisitHistories(String userId) {
        return visitHistoryRepository.findByUserIdOrderByVisitDateDesc(userId);
    }

    public List<VisitHistory> getVisitHistoriesByCategory(String userId, String category) {
        return visitHistoryRepository.findByUserIdAndCategory(userId, category);
    }

    public List<Object[]> getCategoryStats(String userId) {
        return visitHistoryRepository.countVisitsByCategory(userId);
    }

    @Transactional
    public void deleteVisitHistory(Long id, String userId) {
        VisitHistory history = visitHistoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Visit history not found"));

        if (!history.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to delete this visit history");
        }

        visitHistoryRepository.delete(history);
    }

    @Transactional
    public void deleteAllVisitHistories(String userId) {
        List<VisitHistory> userHistories = visitHistoryRepository.findByUserId(userId);
        visitHistoryRepository.deleteAll(userHistories);
    }
}