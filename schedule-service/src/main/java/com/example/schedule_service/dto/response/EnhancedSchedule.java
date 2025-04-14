package com.example.TripSpring.dto.response;

import com.example.TripSpring.dto.domain.Schedule;

import lombok.Data;
@Data
public class EnhancedSchedule {
    private Schedule schedule;
    private PlaceDetailsInfo placeDetails;
    private String schedulingReason;
}
