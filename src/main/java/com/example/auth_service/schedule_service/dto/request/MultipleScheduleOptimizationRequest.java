package com.example.auth_service.schedule_service.dto.request;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class MultipleScheduleOptimizationRequest {
    private List<ScheduleOption> options;

    @Data
    public static class ScheduleOption {
        private Long optionId;
        private List<FixedScheduleDTO> fixedSchedules;
        private List<FlexibleScheduleDTO> flexibleSchedules;
    }

    @Data
    public static class FixedScheduleDTO {
        private String id;
        private String name;
        private String type;
        private int duration;
        private int priority;
        private String location;
        private double latitude;
        private double longitude;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Data
    public static class FlexibleScheduleDTO {
        private String id;
        private String name;
        private String type;
        private int duration;
        private int priority;
    }
}