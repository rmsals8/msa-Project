package com.example.TripSpring.service;

import com.example.TripSpring.dto.domain.Schedule;
import com.example.TripSpring.dto.domain.TrafficInfo;
import com.example.TripSpring.dto.scheduler.OptimizeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.TripSpring.dto.Place;
import com.example.TripSpring.dto.domain.Location;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleOptimizationService {
    private final APIIntegrationService apiIntegrationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MIN_SLOT_DURATION = 30; // 최소 30분

    
    public OptimizeResponse optimizeSchedule(List<Schedule> fixedSchedules, List<Schedule> flexibleSchedules) {
        try {
            // 디버깅 로그 추가
            log.info("Starting schedule optimization with {} fixed and {} flexible schedules",
                fixedSchedules.size(), flexibleSchedules.size());
            
            // 유효성 검사 추가
            if (fixedSchedules.isEmpty()) {
                throw new IllegalArgumentException("At least one fixed schedule is required");
            }
            
            // 1. 초기 작업 - 고정 일정을 시간순으로 정렬
            fixedSchedules.sort(Comparator.comparing(Schedule::getStartTime));
            
            // 2. 최적화 과정을 위한 작업 복사본 생성 (원본 수정 방지)
            List<Schedule> workingSchedules = new ArrayList<>(fixedSchedules);
            
            // 3. 우선순위에 따라 유연한 일정 정렬
            List<Schedule> sortedFlexibleSchedules = new ArrayList<>(flexibleSchedules);
            sortedFlexibleSchedules.sort(Comparator.comparing(Schedule::getPriority));
            
            // 최적화 실패한 일정 목록
            List<Schedule> failedSchedules = new ArrayList<>();
            
            // 4. 각 유연한 일정에 대해 최적 시간 및 장소 찾기
            for (Schedule flexible : sortedFlexibleSchedules) {
                // 4.1 장소 검색 및 최적 시간 찾기
                PlaceTimeResult result = findOptimalPlaceAndTime(flexible, workingSchedules);
                
                if (result != null) {
                    // 4.2 유연한 일정 업데이트 (상세 정보 포함)
                    updateScheduleDetails(
                        flexible,
                        result.getPlace(),
                        result.getStartTime(),
                        result.getEndTime()
                    );
                    
                    // 4.3 최적화된 일정에 추가 (중요: 여기서 시간순 정렬이 적용되어야 함)
                    // 수정된 부분: 단순 추가가 아닌 시간 기반 삽입
                    insertScheduleInOrder(workingSchedules, flexible);
                    
                    log.info("Successfully optimized flexible schedule: {} as {}", 
                        flexible.getName(), result.getPlace().getName());
                } else {
                    log.warn("Failed to optimize flexible schedule: {}", flexible.getName());
                    failedSchedules.add(flexible);
                }
            }
            
            // 5. 최적화 결과 생성
            OptimizeResponse response = createOptimizeResponse(workingSchedules);
            
            // 6. 최적화 결과 로그 기록
            if (!failedSchedules.isEmpty()) {
                double successRate = flexibleSchedules.isEmpty() ? 0 : 
                    (double)(flexibleSchedules.size() - failedSchedules.size()) / flexibleSchedules.size();
                log.info("Optimization completed. Success rate: {}% ({} of {} flexible schedules)",
                        Math.round(successRate * 100),
                        flexibleSchedules.size() - failedSchedules.size(),
                        flexibleSchedules.size());
                log.info("Failed schedules: {}", 
                        failedSchedules.stream().map(Schedule::getName).collect(Collectors.joining(", ")));
            } else if (!flexibleSchedules.isEmpty()) {
                log.info("Optimization completed successfully for all {} flexible schedules", 
                        flexibleSchedules.size());
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Error during schedule optimization", e);
            throw new RuntimeException("Schedule optimization failed: " + e.getMessage(), e);
        }
    }
    

    
// findOptimalPlaceAndTime 메소드 - 고정 일정 사이의 시간 슬롯 우선 고려
// 수정된 메소드 (전체 코드)
private PlaceTimeResult findOptimalPlaceAndTime(Schedule flexible, List<Schedule> existingSchedules) {
    try {
        // 1. 가용 시간대 찾기
        List<TimeSlot> availableSlots = findAvailableTimeSlots(existingSchedules);
        log.info("Found {} available time slots for {}", availableSlots.size(), flexible.getName());
        
        if (availableSlots.isEmpty()) {
            log.warn("No available time slots found for flexible schedule: {}", flexible.getName());
            return null;
        }
        
        // 각 시간대 정보 로깅
        for (int i = 0; i < availableSlots.size(); i++) {
            TimeSlot slot = availableSlots.get(i);
            log.info("Slot {}: {} to {}", i, slot.getStartTime(), slot.getEndTime());
            
            // 슬롯의 이전/다음 일정 확인
            Schedule prevSchedule = slot.getPreviousSchedule();
            Schedule nextSchedule = slot.getNextSchedule();
            
            // 고정 일정 사이 슬롯 여부 로깅
            boolean isBetweenFixed = prevSchedule != null && nextSchedule != null &&
                                     "FIXED".equals(prevSchedule.getType()) && 
                                     "FIXED".equals(nextSchedule.getType());
            if (isBetweenFixed) {
                log.info("⭐ Slot {} is between fixed schedules: {} and {}", 
                        i, prevSchedule.getName(), nextSchedule.getName());
            }
        }
        
        // 2. 모든 장소-시간 조합 생성 및 평가
        List<PlaceTimeOption> allOptions = new ArrayList<>();
        
        // 먼저 고정 일정 사이의 슬롯과 일반 슬롯을 구분
        List<TimeSlot> betweenFixedSlots = new ArrayList<>();
        List<TimeSlot> otherSlots = new ArrayList<>();
        
        for (TimeSlot slot : availableSlots) {
            Schedule prevSchedule = slot.getPreviousSchedule();
            Schedule nextSchedule = slot.getNextSchedule();
            boolean isBetweenFixed = prevSchedule != null && nextSchedule != null &&
                                     "FIXED".equals(prevSchedule.getType()) && 
                                     "FIXED".equals(nextSchedule.getType());
            
            if (isBetweenFixed) {
                betweenFixedSlots.add(slot);
            } else {
                otherSlots.add(slot);
            }
        }
        
        log.info("Found {} slots between fixed schedules and {} other slots", 
                betweenFixedSlots.size(), otherSlots.size());
        
        // 먼저 고정 일정 사이의 슬롯 처리
        processSlots(betweenFixedSlots, existingSchedules, flexible, allOptions, true);
        
        // 고정 일정 사이에 적절한 옵션이 없는 경우에만 다른 슬롯 처리
        if (allOptions.isEmpty()) {
            processSlots(otherSlots, existingSchedules, flexible, allOptions, false);
        }
        
        if (allOptions.isEmpty()) {
            log.warn("No place options found for flexible schedule: {}", flexible.getName());
            return null;
        }
        
        // 3. 점수 기준으로 정렬
        allOptions.sort(Comparator.comparing(PlaceTimeOption::getScore).reversed());
        
        // 최고 점수 옵션 선택
        PlaceTimeOption bestOption = allOptions.get(0);
        
        log.info("Selected best option: {} at {} (score: {}, between fixed: {})",
            bestOption.getPlace().getName(),
            bestOption.getStartTime(),
            bestOption.getScore(),
            bestOption.isBetweenFixed()
        );
        
        return new PlaceTimeResult(
            bestOption.getPlace(),
            bestOption.getStartTime(),
            bestOption.getEndTime()
        );
        
    } catch (Exception e) {
        log.error("Error finding optimal place and time for {}: {}", 
            flexible.getName(), e.getMessage(), e);
        return null;
    }
}

// 슬롯 처리를 위한 새로운 헬퍼 메소드
/**
 * 시간 슬롯을 처리하고 유연 일정 옵션을 생성하는 메소드
 * 고정 일정 사이의 슬롯을 정확히 식별하고 우선 처리
 */
private void processSlots(List<TimeSlot> slots, List<Schedule> existingSchedules, 
                        Schedule flexible, List<PlaceTimeOption> allOptions, boolean isBetweenFixed) {
    for (TimeSlot slot : slots) {
        Schedule prevSchedule = slot.getPreviousSchedule();
        Schedule nextSchedule = slot.getNextSchedule();
        
        // 고정 일정 사이 여부 재확인
        boolean slotIsBetweenFixed = false;
        if (prevSchedule != null && nextSchedule != null) {
            slotIsBetweenFixed = "FIXED".equals(prevSchedule.getType()) && "FIXED".equals(nextSchedule.getType());
        }
        
        // 고정 일정 사이 슬롯 요청과 실제 슬롯 타입이 일치할 때만 처리
        if (isBetweenFixed == slotIsBetweenFixed) {
            // 검색 위치 결정
            Location searchLocation;
            
            if (prevSchedule != null && nextSchedule != null) {
                // 두 일정 사이의 중간 지점 사용
                double midLat = (prevSchedule.getLocation().getLatitude() + nextSchedule.getLocation().getLatitude()) / 2;
                double midLon = (prevSchedule.getLocation().getLongitude() + nextSchedule.getLocation().getLongitude()) / 2;
                searchLocation = new Location(midLat, midLon, "Middle Point");
                log.info("Using midpoint between {} and {} for search", 
                        prevSchedule.getName(), nextSchedule.getName());
            } else if (prevSchedule != null && prevSchedule.getLocation() != null) {
                // 이전 일정 위치 사용
                searchLocation = prevSchedule.getLocation();
                log.info("Using previous schedule location for search: {}", prevSchedule.getName());
            } else if (nextSchedule != null && nextSchedule.getLocation() != null) {
                // 다음 일정 위치 사용
                searchLocation = nextSchedule.getLocation();
                log.info("Using next schedule location for search: {}", nextSchedule.getName());
            } else {
                // 기본 위치 사용
                searchLocation = new Location(35.5383773, 129.3113596, "Default Location");
                log.info("Using default location for search");
            }
            
            // 주변 장소 검색
            List<Place> nearbyPlaces = apiIntegrationService.searchNearbyPlaces(
                flexible.getName(),
                searchLocation.getLatitude(),
                searchLocation.getLongitude(),
                8000
            );
            
            log.info("Found {} places for {} near ({}, {})", 
                    nearbyPlaces.size(), flexible.getName(),
                    searchLocation.getLatitude(), searchLocation.getLongitude());
            
            // 각 장소에 대한 옵션 생성
            for (Place place : nearbyPlaces) {
                // 유연 일정의 시작/종료 시간 결정
                LocalDateTime startTime;
                LocalDateTime endTime;
                
                if (isBetweenFixed) {
                    // 고정 일정 사이일 경우 균등 배분 (이전 일정 종료 후 이동 시간 고려)
                    long availableMinutes = Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
                    long requiredMinutes = flexible.getEstimatedDuration() + 30; // 일정 시간 + 이동 시간
                    
                    if (availableMinutes >= requiredMinutes) {
                        // 이전 일정 후 이동 시간 확보
                        startTime = slot.getStartTime().plusMinutes(15);
                        endTime = startTime.plusMinutes(flexible.getEstimatedDuration());
                        
                        // 다음 일정 전 이동 시간 확보를 위해 필요시 조정
                        if (endTime.plusMinutes(15).isAfter(slot.getEndTime())) {
                            endTime = slot.getEndTime().minusMinutes(15);
                            startTime = endTime.minusMinutes(flexible.getEstimatedDuration());
                        }
                    } else {
                        // 시간이 부족하면 중간에 배치
                        startTime = slot.getStartTime().plus(
                                Duration.between(slot.getStartTime(), slot.getEndTime()).dividedBy(2)
                                .minus(Duration.ofMinutes(flexible.getEstimatedDuration() / 2))
                        );
                        endTime = startTime.plusMinutes(flexible.getEstimatedDuration());
                    }
                } else {
                    // 고정 일정 사이가 아닌 경우 슬롯 시작 시간 사용
                    startTime = slot.getStartTime();
                    endTime = startTime.plusMinutes(flexible.getEstimatedDuration());
                }
                
                // 최적 시작 시간 설정
                place.setOptimalStartTime(startTime);
                
                // 옵션 생성
                PlaceTimeOption option = new PlaceTimeOption(
                    place,
                    startTime,
                    endTime,
                    prevSchedule,
                    nextSchedule,
                    slotIsBetweenFixed
                );
                
                // 점수 계산
                double baseScore = calculatePlaceScore(place, prevSchedule, nextSchedule);
                
                // 고정 일정 사이 가중치 적용 (5배 가중치)
                if (slotIsBetweenFixed) {
                    option.setScore(baseScore * 5.0);
                    log.info("Place '{}' between fixed schedules - score: {}", 
                            place.getName(), option.getScore());
                } else {
                    option.setScore(baseScore);
                }
                
                allOptions.add(option);
            }
        }
    }
}
// 간단한 내부 클래스 - 장소와 시간대 옵션
private static class PlaceTimeOption {
    private final Place place;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final boolean betweenFixed;
    private double score;
    
    public PlaceTimeOption(
            Place place, 
            LocalDateTime startTime, 
            LocalDateTime endTime,
            Schedule previousSchedule,
            Schedule nextSchedule,
            boolean betweenFixed) {
        this.place = place;
        this.startTime = startTime;
        this.endTime = endTime;
        this.betweenFixed = betweenFixed;
    }
    
    public Place getPlace() { return place; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isBetweenFixed() { return betweenFixed; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}

// 장소 점수 계산 헬퍼 메소드
// 장소 점수 계산 헬퍼 메소드 개선
private double calculatePlaceScore(Place place, Schedule prevSchedule, Schedule nextSchedule) {
    double score = 0.0;
    
    // 장소 평점 반영
    if (place.getRating() != null) {
        score += place.getRating() * 0.4; // 최대 2점
    }
    
    // 이전 일정과의 거리 계산
    if (prevSchedule != null && prevSchedule.getLocation() != null) {
        double distFromPrev = calculateDistance(
            prevSchedule.getLocation().getLatitude(),
            prevSchedule.getLocation().getLongitude(),
            place.getGeometry().getLocation().getLat(),
            place.getGeometry().getLocation().getLng()
        );
        
        // 거리 역수로 점수 반영 (가까울수록 높은 점수)
        score += Math.min(5000.0 / (distFromPrev + 500.0), 2.0); // 최대 2점
        
        // 시간 효율성 점수 추가
        if (place.getOptimalStartTime() != null) {
            long timeDiff = java.time.Duration.between(
                prevSchedule.getEndTime(), 
                place.getOptimalStartTime()
            ).toMinutes();
            
            // 이동 시간 예상
            double travelTimeMinutes = distFromPrev / 30.0 * 60; // 평균 시속 30km 가정
            
            // 시간 효율성 점수 (이동 시간 대비 대기 시간)
            double timeEfficiency = 1.0 - Math.min(Math.max(
                (timeDiff - travelTimeMinutes - 15) / 60.0, 0), 1);
            
            score += timeEfficiency * 1.5; // 최대 1.5점
        }
    }
    
    // 다음 일정과의 거리 계산
    if (nextSchedule != null && nextSchedule.getLocation() != null) {
        double distToNext = calculateDistance(
            place.getGeometry().getLocation().getLat(),
            place.getGeometry().getLocation().getLng(),
            nextSchedule.getLocation().getLatitude(),
            nextSchedule.getLocation().getLongitude()
        );
        
        // 거리 역수로 점수 반영 (가까울수록 높은 점수)
        score += Math.min(5000.0 / (distToNext + 500.0), 2.0); // 최대 2점
        
        // 시간 효율성 점수 추가
        if (place.getOptimalStartTime() != null) {
            long visitDuration = 60; // 기본 방문 시간 (분)
            if (place.getMetadata() != null && place.getMetadata().containsKey("visitDuration")) {
                visitDuration = ((Number) place.getMetadata().get("visitDuration")).longValue();
            }
            
            LocalDateTime endTime = place.getOptimalStartTime().plusMinutes(visitDuration);
            long timeDiff = java.time.Duration.between(
                endTime,
                nextSchedule.getStartTime()
            ).toMinutes();
            
            // 이동 시간 예상
            double travelTimeMinutes = distToNext / 30.0 * 60; // 평균 시속 30km 가정
            
            // 시간 효율성 점수 (이동 시간 대비 대기 시간)
            double timeEfficiency = 1.0 - Math.min(Math.max(
                (timeDiff - travelTimeMinutes - 15) / 60.0, 0), 1);
            
            score += timeEfficiency * 1.5; // 최대 1.5점
        }
    }
    
    // 양쪽 일정이 모두 있는 경우 (고정 일정 사이) 추가 점수
    if (prevSchedule != null && nextSchedule != null) {
        score += 2.0; // 고정 일정 사이에 있으면 기본 2점 추가
    }
    
    return score;
}

// 거리 계산 유틸리티 (하버사인 공식)
private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
    double earthRadius = 6371; // 지구 반경(km)
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
               Math.sin(dLon / 2) * Math.sin(dLon / 2);
    
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    
    return earthRadius * c; // 킬로미터 단위 거리
}

// 결과 반환 클래스
@lombok.Value
private static class PlaceTimeResult {
    Place place;
    LocalDateTime startTime;
    LocalDateTime endTime;
}
    



    
 /**
 * 사용 가능한 시간 슬롯을 찾는 메소드
 * 고정 일정 사이의 슬롯을 정확히 식별하도록 개선
 */
private List<TimeSlot> findAvailableTimeSlots(List<Schedule> schedules) {
    List<TimeSlot> slots = new ArrayList<>();
    LocalDateTime currentTime = LocalDateTime.now().withMinute(0).withSecond(0);
    
    if (schedules.isEmpty()) {
        // 일정이 없는 경우 오늘 업무 시간 전체를 하나의 슬롯으로
        slots.add(new TimeSlot(
            currentTime.withHour(9),
            currentTime.withHour(18)
        ));
        return slots;
    }
    
    // 시간순 정렬
    schedules.sort(Comparator.comparing(Schedule::getStartTime));
    
    // 첫 일정 이전 시간대 확인
    Schedule firstSchedule = schedules.get(0);
    if (firstSchedule.getStartTime().isAfter(currentTime.plusMinutes(MIN_SLOT_DURATION))) {
        TimeSlot beforeFirst = new TimeSlot(
            currentTime, 
            firstSchedule.getStartTime(),
            null,
            firstSchedule
        );
        slots.add(beforeFirst);
        log.info("Found time slot before first schedule: {} to {}", 
                currentTime, firstSchedule.getStartTime());
    }
    
    // 일정들 사이의 시간대 확인
    for (int i = 0; i < schedules.size() - 1; i++) {
        Schedule current = schedules.get(i);
        Schedule next = schedules.get(i + 1);
        
        LocalDateTime slotStart = current.getEndTime();
        LocalDateTime slotEnd = next.getStartTime();
        
        // 최소 필요 시간 이상의 간격이 있는 경우에만 슬롯 추가
        if (Duration.between(slotStart, slotEnd).toMinutes() >= MIN_SLOT_DURATION) {
            TimeSlot betweenSlot = new TimeSlot(
                slotStart,
                slotEnd,
                current,
                next
            );
            slots.add(betweenSlot);
            
            // 고정 일정 사이 여부 로깅
            boolean isBetweenFixed = "FIXED".equals(current.getType()) && "FIXED".equals(next.getType());
            if (isBetweenFixed) {
                log.info("Found time slot between fixed schedules {} and {}: {} to {}", 
                        current.getName(), next.getName(), slotStart, slotEnd);
            } else {
                log.info("Found time slot between schedules (not both fixed): {} to {}", 
                        slotStart, slotEnd);
            }
        }
    }
    
    // 마지막 일정 이후 시간대 확인
    Schedule lastSchedule = schedules.get(schedules.size() - 1);
    LocalDateTime endOfDay = currentTime.withHour(22).withMinute(0);
    
    if (lastSchedule.getEndTime().plusMinutes(MIN_SLOT_DURATION).isBefore(endOfDay)) {
        TimeSlot afterLast = new TimeSlot(
            lastSchedule.getEndTime(),
            endOfDay,
            lastSchedule,
            null
        );
        slots.add(afterLast);
        log.info("Found time slot after last schedule: {} to {}", 
                lastSchedule.getEndTime(), endOfDay);
    }
    
    // 슬롯 크기 기준으로 정렬 (큰 슬롯 우선)
    slots.sort((a, b) -> {
        long durationA = Duration.between(a.getStartTime(), a.getEndTime()).toMinutes();
        long durationB = Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
        return Long.compare(durationB, durationA);
    });
    
    return slots;
}
    
 
 
    
// insertScheduleInOrder 메소드는 그대로 유지
// 수정된 insertScheduleInOrder 메소드 - 시간 기반 정렬 보장
// 수정된 insertScheduleInOrder 메소드
/**
 * 유연한 일정을 적절한 위치에 삽입하는 메소드.
 * 고정 일정 사이에 유연한 일정을 삽입하도록 우선순위를 부여합니다.
 */
private void insertScheduleInOrder(List<Schedule> schedules, Schedule newSchedule) {
    if (schedules.isEmpty()) {
        schedules.add(newSchedule);
        log.info("유연한 일정 '{}' 첫 번째 일정으로 추가됨", newSchedule.getName());
        return;
    }
    
    // 유연한 일정인 경우만 특별 처리
    if ("FLEXIBLE".equals(newSchedule.getType())) {
        // 고정 일정 사이에 들어갈 수 있는 위치 찾기
        List<BetweenSlotsInfo> betweenSlots = findBetweenFixedSlots(schedules, newSchedule);
        
        // 배치 가능한 고정 일정 사이 슬롯이 있는 경우
        if (!betweenSlots.isEmpty()) {
            // 가장 적합한 슬롯 선택 (시간 간격과 거리 기반으로 점수 계산)
            BetweenSlotsInfo bestSlot = findBestBetweenSlot(betweenSlots, newSchedule);
            
            // 선택된 고정 일정 사이에 삽입
            int insertIndex = bestSlot.getNextScheduleIndex();
            schedules.add(insertIndex, newSchedule);
            
            log.info("유연한 일정 '{}' 고정 일정 '{}'-'{}' 사이에 삽입됨 (인덱스: {})", 
                    newSchedule.getName(), 
                    bestSlot.getPrevSchedule().getName(),
                    bestSlot.getNextSchedule().getName(),
                    insertIndex);
            
            return;
        }
    }
    
    // 고정 일정 사이에 삽입할 수 없는 경우, 기존 방식으로 시간순 삽입
    int insertIndex = 0;
    while (insertIndex < schedules.size() && 
           !schedules.get(insertIndex).getStartTime().isAfter(newSchedule.getStartTime())) {
        insertIndex++;
    }
    
    schedules.add(insertIndex, newSchedule);
    log.info("유연한 일정 '{}' 시간순으로 삽입됨 (인덱스: {})", newSchedule.getName(), insertIndex);
    
    // 시간 겹침 확인 및 조정
    adjustOverlappingTimes(schedules, insertIndex);
}

/**
 * 고정 일정 사이의 적합한 슬롯을 찾는 메소드
 */
private List<BetweenSlotsInfo> findBetweenFixedSlots(List<Schedule> schedules, Schedule newSchedule) {
    List<BetweenSlotsInfo> betweenSlots = new ArrayList<>();
    
    // 연속된 고정 일정 쌍 찾기
    for (int i = 0; i < schedules.size() - 1; i++) {
        Schedule current = schedules.get(i);
        Schedule next = schedules.get(i + 1);
        
        // 둘 다 고정 일정인 경우만 처리
        if ("FIXED".equals(current.getType()) && "FIXED".equals(next.getType())) {
            // 일정 사이 시간이 충분한지 확인 (유연 일정 소요 시간 + 최소 이동 시간)
            int requiredMinutes = getRequiredMinutesForSchedule(newSchedule);
            long availableMinutes = java.time.Duration.between(
                    current.getEndTime(), next.getStartTime()).toMinutes();
            
            if (availableMinutes >= requiredMinutes) {
                BetweenSlotsInfo slotInfo = new BetweenSlotsInfo();
                slotInfo.setPrevSchedule(current);
                slotInfo.setNextSchedule(next);
                slotInfo.setNextScheduleIndex(i + 1);
                slotInfo.setAvailableMinutes(availableMinutes);
                
                // 시간 적합성 점수 계산 (사용 가능 시간과 필요 시간의 비율 - 너무 크거나 작지 않을수록 좋음)
                double timeScore = calculateTimeScore(availableMinutes, requiredMinutes);
                slotInfo.setTimeScore(timeScore);
                
                // 거리 적합성 점수 계산 (전후 일정과의 거리 최소화)
                double distanceScore = calculateDistanceScore(current, next, newSchedule);
                slotInfo.setDistanceScore(distanceScore);
                
                // 종합 점수 계산
                slotInfo.setTotalScore(timeScore * 0.6 + distanceScore * 0.4);
                
                betweenSlots.add(slotInfo);
            }
        }
    }
    
    return betweenSlots;
}

/**
 * 가장 적합한 고정 일정 사이 슬롯 찾기
 */
private BetweenSlotsInfo findBestBetweenSlot(List<BetweenSlotsInfo> betweenSlots, Schedule newSchedule) {
    return betweenSlots.stream()
            .max(Comparator.comparing(BetweenSlotsInfo::getTotalScore))
            .orElse(betweenSlots.get(0));
}

/**
 * 일정 사이 시간 적합성 점수 계산 (이상적인 비율에 가까울수록 높은 점수)
 */
private double calculateTimeScore(long availableMinutes, int requiredMinutes) {
    // 이상적인 비율: 필요 시간의 1.5배
    double idealRatio = 1.5;
    double actualRatio = (double) availableMinutes / requiredMinutes;
    
    // 너무 빡빡하거나 너무 느슨하지 않은 비율일수록 높은 점수 (종 모양 커브)
    if (actualRatio < 1.0) {
        return actualRatio; // 필요 시간보다 적으면 비율 그대로 반환 (1.0 미만)
    } else {
        // 이상적인 비율에 가까울수록 1.0에 가까운 점수
        return Math.max(0, 1.0 - 0.2 * Math.abs(actualRatio - idealRatio));
    }
}

/**
 * 거리 적합성 점수 계산 (전후 일정과의 거리가 적절할수록 높은 점수)
 */
private double calculateDistanceScore(Schedule prev, Schedule next, Schedule newSchedule) {
    // 두 고정 일정 사이의 중간 지점과 새 일정 위치의 거리

    
    // 직선 거리가 아닌 경로 상의 위치를 고려하기 위해 두 고정 일정으로부터의 거리 합 계산
    double distFromPrev = calculateDistance(
            prev.getLocation().getLatitude(), 
            prev.getLocation().getLongitude(),
            newSchedule.getLocation().getLatitude(),
            newSchedule.getLocation().getLongitude());
    
    double distToNext = calculateDistance(
            newSchedule.getLocation().getLatitude(),
            newSchedule.getLocation().getLongitude(),
            next.getLocation().getLatitude(),
            next.getLocation().getLongitude());
    
    // 두 고정 일정 간 직접 거리
    double directDist = calculateDistance(
            prev.getLocation().getLatitude(),
            prev.getLocation().getLongitude(),
            next.getLocation().getLatitude(),
            next.getLocation().getLongitude());
    
    // 최적 점수는 경로 거리와 직접 거리가 유사할 때 (우회 최소화)
    double detourRatio = (distFromPrev + distToNext) / Math.max(directDist, 0.1);
    
    // 우회율이 낮을수록 높은 점수 (최대 1.0)
    return Math.max(0, 1.0 - Math.min(1.0, (detourRatio - 1.0) * 0.5));
}

/**
 * 일정에 필요한 최소 시간 계산 (소요 시간 + 이동 시간)
 */
private int getRequiredMinutesForSchedule(Schedule schedule) {
    // 일정 자체 소요 시간
    int durationMinutes = schedule.getEstimatedDuration();
    
    // 최소 이동 시간 (앞뒤 각 15분)
    int minTravelTime = 30;
    
    return durationMinutes + minTravelTime;
}

/**
 * 시간 겹침 해결을 위한 일정 조정
 */
private void adjustOverlappingTimes(List<Schedule> schedules, int insertedIndex) {
    if (schedules.size() <= 1 || insertedIndex >= schedules.size()) {
        return;
    }
    
    Schedule current = schedules.get(insertedIndex);
    
    // 이전 일정과 겹치는 경우
    if (insertedIndex > 0) {
        Schedule prev = schedules.get(insertedIndex - 1);
        if (current.getStartTime().isBefore(prev.getEndTime())) {
            // 유연 일정인 경우 시작 시간 조정
            if ("FLEXIBLE".equals(current.getType())) {
                LocalDateTime newStart = prev.getEndTime().plusMinutes(15);
                int duration = (int) java.time.Duration.between(
                        current.getStartTime(), current.getEndTime()).toMinutes();
                
                current.setStartTime(newStart);
                current.setEndTime(newStart.plusMinutes(duration));
                
                log.info("유연 일정 '{}' 시간 조정: {} ~ {}", 
                        current.getName(), current.getStartTime(), current.getEndTime());
            }
        }
    }
    
    // 다음 일정과 겹치는 경우
    if (insertedIndex < schedules.size() - 1) {
        Schedule next = schedules.get(insertedIndex + 1);
        if (current.getEndTime().isAfter(next.getStartTime())) {
            // 다음 일정이 고정이고 현재 일정이 유연인 경우
            if ("FIXED".equals(next.getType()) && "FLEXIBLE".equals(current.getType())) {
                LocalDateTime newEnd = next.getStartTime().minusMinutes(15);
                
                // 최소 소요 시간 보장 (30분)
                LocalDateTime minEndTime = current.getStartTime().plusMinutes(30);
                if (newEnd.isBefore(minEndTime)) {
                    // 시간이 부족하면 시작 시간도 당김
                    LocalDateTime newStart = newEnd.minusMinutes(30);
                    current.setStartTime(newStart);
                }
                
                current.setEndTime(newEnd);
                log.info("유연 일정 '{}' 종료 시간 조정: {}", current.getName(), current.getEndTime());
            }
        }
    }
}

/**
 * 고정 일정 사이 슬롯 정보 클래스
 */
@lombok.Data
private static class BetweenSlotsInfo {
    private Schedule prevSchedule;
    private Schedule nextSchedule;
    private int nextScheduleIndex;
    private long availableMinutes;
    private double timeScore;
    private double distanceScore;
    private double totalScore;
}


    
    private OptimizeResponse createOptimizeResponse(List<Schedule> optimizedSchedules) {
        OptimizeResponse response = new OptimizeResponse();
        
        // 1. 최적화된 일정 설정
        response.setOptimizedSchedules(optimizedSchedules);
        
        // 2. 경로 세그먼트 생성
        List<OptimizeResponse.RouteSegment> segments = new ArrayList<>();
        for (int i = 0; i < optimizedSchedules.size() - 1; i++) {
            Schedule current = optimizedSchedules.get(i);
            Schedule next = optimizedSchedules.get(i + 1);
            
            TrafficInfo trafficInfo = apiIntegrationService.getIntegratedTrafficInfo(
                current.getLocation(),
                next.getLocation(),
                current.getEndTime()
            );
            
            OptimizeResponse.RouteSegment segment = new OptimizeResponse.RouteSegment();
            segment.setFromLocation(current.getName());
            segment.setToLocation(next.getName());
            segment.setDistance(trafficInfo.getDistance());
            segment.setEstimatedTime(trafficInfo.getEstimatedTime());
            segment.setTrafficRate(trafficInfo.getTrafficRate());
            
            segments.add(segment);
        }
        response.setRouteSegments(segments);
        
        // 3. 최적화 메트릭스 설정
        OptimizeResponse.OptimizationMetrics metrics = new OptimizeResponse.OptimizationMetrics();
        metrics.setTotalDistance(segments.stream().mapToDouble(OptimizeResponse.RouteSegment::getDistance).sum());
        metrics.setTotalTime(segments.stream().mapToInt(OptimizeResponse.RouteSegment::getEstimatedTime).sum());
        response.setMetrics(metrics);
        
        // 4. 일정별 분석 정보 설정
        Map<String, OptimizeResponse.ScheduleAnalysis> analyses = new HashMap<>();
        for (Schedule schedule : optimizedSchedules) {
            OptimizeResponse.ScheduleAnalysis analysis = new OptimizeResponse.ScheduleAnalysis();
            analysis.setLocationName(schedule.getName());
            
            Map<String, Object> placeInfo = apiIntegrationService.getIntegratedLocationInfo(
                schedule.getName(),
                schedule.getLocation(),
                schedule.getStartTime()
            );
            
            analysis.setPlaceDetails(placeInfo);
            analysis.setCrowdLevel((double) placeInfo.get("crowdLevel"));
            
            analyses.put(schedule.getName(), analysis);
        }
        response.setScheduleAnalyses(analyses);
        
        return response;
    }
    
    @lombok.Value
    public static class TimeSlot {
        LocalDateTime startTime;
        LocalDateTime endTime;
        Schedule previousSchedule;
        Schedule nextSchedule;

            // 기본 생성자 (이전 코드와의 호환성)
    public TimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.previousSchedule = null;
        this.nextSchedule = null;
    }

    // 전체 필드 생성자
    public TimeSlot(LocalDateTime startTime, LocalDateTime endTime, 
                   Schedule previousSchedule, Schedule nextSchedule) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.previousSchedule = previousSchedule;
        this.nextSchedule = nextSchedule;
    }   
    }
    
    @lombok.Value
    private static class OptimizationResult {
        LocalDateTime startTime;
        LocalDateTime endTime;
        double score;
        Map<String, Double> componentScores;
    }

    private void updateScheduleDetails(Schedule schedule, Place place, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            // 위치 문자열 설정 - JSON 형태로 더 많은 정보 포함
            Map<String, Object> locationInfo = new HashMap<>();
            locationInfo.put("name", place.getName());
            locationInfo.put("address", place.getFormatted_address());
            locationInfo.put("latitude", place.getGeometry().getLocation().getLat());
            locationInfo.put("longitude", place.getGeometry().getLocation().getLng());
            
            // 추가 정보 포함
            if (place.getMetadata() != null) {
                if (place.getMetadata().containsKey("distance")) {
                    locationInfo.put("distance", place.getMetadata().get("distance"));
                }
                if (place.getMetadata().containsKey("source")) {
                    locationInfo.put("source", place.getMetadata().get("source"));
                }
            }
            
            // 평점 추가
            locationInfo.put("rating", place.getRating());
            
            // JSON으로 변환
            String locationJson;
            try {
                locationJson = objectMapper.writeValueAsString(locationInfo);
            } catch (Exception e) {
                log.warn("Failed to serialize location info, using basic format", e);
                locationJson = place.getName() + " (" + place.getFormatted_address() + ")";
            }
            
            // Schedule 객체에 설정
            schedule.setLocationString(locationJson);
            
            // Location 객체 생성
            Location location = new Location(
                place.getGeometry().getLocation().getLat(),
                place.getGeometry().getLocation().getLng(),
                place.getName()
            );
            schedule.setLocation(location);
            
            // 일정 시간 설정
            schedule.setStartTime(startTime);
            schedule.setEndTime(endTime);
            
            // 모델 타입에 따라 추가 필드가 있으면 설정
            try {
                // "displayName" 필드가 있는지 확인하고 설정 (장소명 표시용)
                java.lang.reflect.Method setDisplayNameMethod = 
                    schedule.getClass().getMethod("setDisplayName", String.class);
                setDisplayNameMethod.invoke(schedule, place.getName());
            } catch (NoSuchMethodException e) {
                // 해당 필드가 없으면 무시
            } catch (Exception e) {
                log.warn("Failed to set display name", e);
            }
        } catch (Exception e) {
            log.error("Error updating schedule details: {}", e.getMessage(), e);
            
            // 기본 업데이트 시도
            schedule.setLocationString(place.getName() + " (" + place.getFormatted_address() + ")");
            
            Location location = new Location(
                place.getGeometry().getLocation().getLat(),
                place.getGeometry().getLocation().getLng(),
                place.getName()
            );
            schedule.setLocation(location);
            schedule.setStartTime(startTime);
            schedule.setEndTime(endTime);
        }
    }
    
}