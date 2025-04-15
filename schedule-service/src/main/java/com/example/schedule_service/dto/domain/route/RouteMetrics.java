package com.example.schedule_service.dto.domain.route;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class RouteMetrics {
    private double distance;
    private int duration;
    private double trafficRate;
    private String difficulty;
}