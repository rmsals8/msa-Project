package com.example.schedule_service.dto.response;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class PlaceDetailsInfo {
    private List<String> peakHours;
    private double currentCrowdLevel;
    private String optimalVisitTime;
}
