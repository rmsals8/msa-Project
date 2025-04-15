package com.example.schedule_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Location {
    private String navigationId;
    private Double latitude;
    private Double longitude;
    private Long timestamp;
    private Double accuracy;
}