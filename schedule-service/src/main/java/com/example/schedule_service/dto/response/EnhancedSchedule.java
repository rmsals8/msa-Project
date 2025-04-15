package com.example.schedule_service.dto.response;

import com.example.schedule_service.dto.domain.Schedule;

import lombok.Data;
@Data
public class EnhancedSchedule {
    private Schedule schedule;
    private PlaceDetailsInfo placeDetails;
    private String schedulingReason;
}
