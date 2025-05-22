// SavedScheduleResponse.java
package com.example.auth_service.schedule_service.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SavedScheduleResponse {
    private Long id;
    private String scheduleName;
    private LocalDateTime createdAt;
    private LocalDateTime expirationDate;
    private Double totalDistance;
    private Integer totalTime;
    private Double totalCost;
    private List<ScheduleItemResponse> scheduleItems;
    private List<SegmentResponse> segments;

    @Data
    @Builder
    public static class ScheduleItemResponse {
        private String name;
        private String location;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String type;
    }

    @Data
    @Builder
    public static class SegmentResponse {
        private String fromLocation;
        private String toLocation;
        private Double distance;
        private Integer duration;
        private String transportMode;
    }
}