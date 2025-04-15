package com.example.schedule_service.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TimeWindow {
    private LocalDateTime start;
    private LocalDateTime end;
    private Schedule previousSchedule;
    private Schedule nextSchedule;
}
