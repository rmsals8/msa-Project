package com.example.schedule_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Geometry {
    private Location location;

    @Data
    @NoArgsConstructor
    public static class Location {
        private double lat;
        private double lng;
    }
}