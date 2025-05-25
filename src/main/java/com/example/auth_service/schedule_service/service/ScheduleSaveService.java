package com.example.auth_service.schedule_service.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth_service.schedule_service.domain.*;
import com.example.auth_service.schedule_service.dto.request.SaveScheduleRequest;
import com.example.auth_service.schedule_service.dto.response.SavedScheduleListResponse;
import com.example.auth_service.schedule_service.dto.response.SavedScheduleResponse;
import com.example.auth_service.schedule_service.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSaveService {
        private final SavedScheduleRepository savedScheduleRepository;
        private final SavedScheduleItemRepository savedScheduleItemRepository;
        private final SavedScheduleSegmentRepository savedScheduleSegmentRepository;

        private static final int FREE_USER_LIMIT = 3;
        private static final int PREMIUM_USER_LIMIT = 5;

        @Transactional
        public SavedScheduleResponse saveSchedule(Long userNo, SaveScheduleRequest request) {
                // 1. 사용자 정보 조회 및 저장 가능 여부 확인
                String userTier = "FREE"; // 기본값은 FREE로 설정
                int maxLimit = FREE_USER_LIMIT; // 기본 제한은 FREE 사용자 기준

                // 현재 활성화된 저장 일정 개수 확인
                int currentCount = savedScheduleRepository.countActiveByUserNo(userNo, LocalDateTime.now());
                if (currentCount >= maxLimit) {
                        throw new IllegalStateException(
                                        "저장 가능한 일정 개수를 초과했습니다. 현재: " + currentCount + "개, 최대: " + maxLimit + "개");
                }

                // 2. SavedSchedule 엔티티 생성 및 저장
                SavedSchedule savedSchedule = SavedSchedule.builder()
                                .userNo(userNo)
                                .scheduleName(request.getScheduleName())
                                .createdAt(LocalDateTime.now())
                                .expirationDate(LocalDateTime.now().plusDays(request.getExpirationDays()))
                                .totalDistance(request.getMetrics().getTotalDistance())
                                .totalTime(request.getMetrics().getTotalDuration())
                                .totalCost(request.getMetrics().getTotalCost())
                                .build();

                savedSchedule = savedScheduleRepository.save(savedSchedule);

                // 3. SavedScheduleItem 저장
                int sequence = 1;
                for (SaveScheduleRequest.OptimizedScheduleDTO optimizedSchedule : request.getOptimizedSchedules()) {
                        SavedScheduleItem item = SavedScheduleItem.builder()
                                        .savedSchedule(savedSchedule)
                                        .sequenceNo(sequence++)
                                        .name(optimizedSchedule.getName())
                                        .location(optimizedSchedule.getLocation().getName())
                                        .latitude(optimizedSchedule.getLocation().getLatitude())
                                        .longitude(optimizedSchedule.getLocation().getLongitude())
                                        .startTime(LocalDateTime.parse(optimizedSchedule.getStartTime()))
                                        .endTime(LocalDateTime.parse(optimizedSchedule.getEndTime()))
                                        .type(optimizedSchedule.getType())
                                        .priority(optimizedSchedule.getPriority())
                                        .duration(optimizedSchedule.getDuration())
                                        .build();

                        savedScheduleItemRepository.save(item);
                }

                // 4. SavedScheduleSegment 저장
                List<SavedScheduleItem> items = savedScheduleItemRepository
                                .findBySavedScheduleIdOrderBySequenceNo(savedSchedule.getId());

                int index = 0;
                for (SaveScheduleRequest.RouteSegmentDTO segment : request.getSegments()) {
                        if (index < items.size() - 1) {
                                SavedScheduleSegment savedSegment = SavedScheduleSegment.builder()
                                                .savedSchedule(savedSchedule)
                                                .fromItem(items.get(index))
                                                .toItem(items.get(index + 1))
                                                .distance(segment.getDistance())
                                                .duration(segment.getDuration())
                                                .transportMode(segment.getTransportMode())
                                                .build();

                                savedScheduleSegmentRepository.save(savedSegment);
                                index++;
                        }
                }

                // 5. 응답 생성
                return convertToResponse(savedSchedule);
        }

        @Transactional(readOnly = true)
        public List<SavedScheduleListResponse> getUserSavedSchedules(Long userNo) {
                List<SavedSchedule> schedules = savedScheduleRepository.findActiveByUserNo(userNo, LocalDateTime.now());

                return schedules.stream()
                                .map(this::convertToListResponse)
                                .collect(Collectors.toList());
        }

        @SuppressWarnings("unchecked")
        @Transactional(readOnly = true)
        public SavedScheduleResponse getSavedScheduleDetail(Long scheduleId, Long userNo) {
                // 일정 기본 정보 로딩
                SavedSchedule schedule = savedScheduleRepository.findByIdAndUserNo(scheduleId, userNo)
                                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));

                // 일정 항목과 세그먼트 개별적으로 로딩
                List<SavedScheduleItem> items = savedScheduleItemRepository
                                .findBySavedScheduleIdOrderBySequenceNo(schedule.getId());
                List<SavedScheduleSegment> segments = savedScheduleSegmentRepository
                                .findBySavedScheduleId(schedule.getId());

                // 객체에 직접 설정 (반드시 getter/setter가 있어야 함)
                schedule.setScheduleItems(items);

                // segments가 List 타입인 경우:
                if (schedule.getSegments() instanceof List) {
                        ((List<SavedScheduleSegment>) schedule.getSegments()).clear();
                        ((List<SavedScheduleSegment>) schedule.getSegments()).addAll(segments);
                }
                // segments가 Set 타입인 경우:
                else if (schedule.getSegments() instanceof Set) {
                        schedule.getSegments().clear();
                        schedule.getSegments().addAll(segments);
                }

                // 기존 메서드 사용
                return convertToResponse(schedule);
        }

        @PersistenceContext
        private EntityManager entityManager;

        @Transactional
        public void deleteSavedSchedule(Long scheduleId, Long userNo) {
                // 해당 일정이 존재하는지 확인
                boolean exists = savedScheduleRepository.existsByIdAndUserNo(scheduleId, userNo);
                if (!exists) {
                        throw new IllegalArgumentException("일정을 찾을 수 없습니다.");
                }

                try {
                        // 직접 SQL 쿼리를 사용하여 순서대로 삭제
                        // 1. 먼저 세그먼트 삭제
                        entityManager.createNativeQuery(
                                        "DELETE FROM saved_schedule_segments WHERE saved_schedule_id = :scheduleId")
                                        .setParameter("scheduleId", scheduleId)
                                        .executeUpdate();
                        log.info("세그먼트 삭제 완료: scheduleId={}", scheduleId);

                        // 2. 그 다음 항목 삭제
                        entityManager.createNativeQuery(
                                        "DELETE FROM saved_schedule_items WHERE saved_schedule_id = :scheduleId")
                                        .setParameter("scheduleId", scheduleId)
                                        .executeUpdate();
                        log.info("항목 삭제 완료: scheduleId={}", scheduleId);

                        // 3. 마지막으로 일정 자체 삭제
                        entityManager.createNativeQuery(
                                        "DELETE FROM saved_schedules WHERE id = :scheduleId AND user_no = :userNo")
                                        .setParameter("scheduleId", scheduleId)
                                        .setParameter("userNo", userNo)
                                        .executeUpdate();
                        log.info("일정 삭제 완료: scheduleId={}", scheduleId);

                } catch (Exception e) {
                        log.error("일정 삭제 중 오류 발생: {}", e.getMessage(), e);
                        throw new RuntimeException("일정 삭제 중 오류가 발생했습니다: " + e.getMessage(), e);
                }
        }

        private SavedScheduleResponse convertToResponse(SavedSchedule schedule) {
                List<SavedScheduleItem> items = savedScheduleItemRepository
                                .findBySavedScheduleIdOrderBySequenceNo(schedule.getId());
                List<SavedScheduleSegment> segments = savedScheduleSegmentRepository
                                .findBySavedScheduleId(schedule.getId());

                return SavedScheduleResponse.builder()
                                .id(schedule.getId())
                                .scheduleName(schedule.getScheduleName())
                                .createdAt(schedule.getCreatedAt())
                                .expirationDate(schedule.getExpirationDate())
                                .totalDistance(schedule.getTotalDistance())
                                .totalTime(schedule.getTotalTime())
                                .totalCost(schedule.getTotalCost())
                                .scheduleItems(items.stream()
                                                .map(item -> SavedScheduleResponse.ScheduleItemResponse.builder()
                                                                .name(item.getName())
                                                                .location(item.getLocation())
                                                                .startTime(item.getStartTime())
                                                                .endTime(item.getEndTime())
                                                                .type(item.getType())
                                                                .build())
                                                .collect(Collectors.toList()))
                                .segments(segments.stream()
                                                .map(segment -> SavedScheduleResponse.SegmentResponse.builder()
                                                                .fromLocation(segment.getFromItem().getName())
                                                                .toLocation(segment.getToItem().getName())
                                                                .distance(segment.getDistance())
                                                                .duration(segment.getDuration())
                                                                .transportMode(segment.getTransportMode())
                                                                .build())
                                                .collect(Collectors.toList()))
                                .build();
        }

        private SavedScheduleListResponse convertToListResponse(SavedSchedule schedule) {
                return SavedScheduleListResponse.builder()
                                .id(schedule.getId())
                                .scheduleName(schedule.getScheduleName())
                                .createdAt(schedule.getCreatedAt())
                                .expirationDate(schedule.getExpirationDate())
                                .totalDistance(schedule.getTotalDistance())
                                .totalTime(schedule.getTotalTime())
                                .totalCost(schedule.getTotalCost())
                                .itemCount(schedule.getScheduleItems().size())
                                .build();
        }
}