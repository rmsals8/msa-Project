// SavedScheduleListResponse.java
package com.example.auth_service.schedule_service.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SavedScheduleListResponse {
    private Long id;
    private String scheduleName;
    private LocalDateTime createdAt;
    private LocalDateTime expirationDate;
    private Double totalDistance;
    private Integer totalTime;
    private Double totalCost;
    private Integer itemCount;
}