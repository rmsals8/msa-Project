package com.example.schedule_service.scheduler;

import com.example.schedule_service.domain.SavedSchedule;
import com.example.schedule_service.repository.SavedScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleExpirationScheduler {
    
    private final SavedScheduleRepository savedScheduleRepository;
    
    // 매일 자정에 실행
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markExpiredSchedulesAsDeleted() {
        log.info("만료된 일정 자동 삭제 작업 시작");
        
        LocalDateTime now = LocalDateTime.now();
        List<SavedSchedule> expiredSchedules = savedScheduleRepository.findExpiredSchedules(now);
        
        for (SavedSchedule schedule : expiredSchedules) {
            schedule.setDeleted(true);
            schedule.setDeletedAt(now);
            savedScheduleRepository.save(schedule);
            
            log.info("일정 만료 처리: ID={}, 사용자={}, 만료일={}", 
                    schedule.getId(), schedule.getUserNo(), schedule.getExpirationDate());
        }
        
        log.info("만료된 일정 자동 삭제 작업 완료: {}개 처리됨", expiredSchedules.size());
    }
}