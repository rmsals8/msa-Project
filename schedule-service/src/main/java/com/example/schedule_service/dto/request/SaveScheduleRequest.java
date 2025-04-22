// SaveScheduleRequest.java
package com.example.schedule_service.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class SaveScheduleRequest {
    private String scheduleName;
    private Integer expirationDays; // 1-7
    private List<OptimizedScheduleDTO> optimizedSchedules;
    private List<RouteSegmentDTO> segments;
    private RouteMetricsDTO metrics;
    
    @Data
    public static class OptimizedScheduleDTO {
        private String name;
        private LocationDTO location;
        private String startTime;
        private String endTime;
        private String type;
        private Integer priority;
        private Integer duration;
    }
    
    @Data
    public static class LocationDTO {
        private Double latitude;
        private Double longitude;
        private String name;
    }
    
    @Data
    public static class RouteSegmentDTO {
        private String fromLocation;
        private String toLocation;
        private Double distance;
        private Integer duration;
        private String transportMode;
    }
    
    @Data
    public static class RouteMetricsDTO {
        private Double totalDistance;
        private Integer totalDuration;
        private Double totalCost;
    }
}