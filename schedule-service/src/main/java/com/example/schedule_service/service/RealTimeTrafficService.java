//src/main/java/com/example/TripSpring/service/traffic/RealTimeTrafficService.java
package com.example.TripSpring.service;

import com.example.TripSpring.dto.domain.Location;
import com.example.TripSpring.dto.traffic.TrafficStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeTrafficService {
    private final TmapService tmapService;
    private final Map<String, TrafficStatus> trafficCache = new ConcurrentHashMap<>();
    
    @Scheduled(fixedRate = 30000)  // 30초마다 업데이트
    public void updateTrafficInfo() {
        trafficCache.forEach((key, status) -> {
            try {
                Location location = status.getLocation();
                String trafficInfo = tmapService.getTrafficInfo(
                    location.getLatitude(),
                    location.getLongitude()
                );
                
                TrafficStatus updatedStatus = parseTrafficInfo(trafficInfo);
                trafficCache.put(key, updatedStatus);
                
                log.debug("Updated traffic info for {}: {}", key, updatedStatus);
            } catch (Exception e) {
                log.error("Failed to update traffic info for {}: {}", key, e.getMessage());
            }
        });
    }
    
    public TrafficStatus getTrafficStatus(Location location) {
        try {
             
            
            return TrafficStatus.builder()
                .location(location)
                .congestionLevel(0.5) // 기본값
                .averageSpeed(30)     // 기본값
                .status("보통")       // 기본값
                .incidents(Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("Error getting traffic status: {}", e.getMessage());
            return createDefaultTrafficStatus(location);
        }
    }
    
    private TrafficStatus parseTrafficInfo(String trafficInfo) {
        try {
            // Use parameterized type for Map
            Map<String, Object> info = new ObjectMapper().readValue(trafficInfo, 
                    new TypeReference<Map<String, Object>>() {});
            
            double congestion = extractCongestion(info);
            int speed = extractSpeed(info);
            String status = determineCongestionStatus(congestion);
            List<String> incidents = extractIncidents(info);
            
            return TrafficStatus.builder()
                .congestionLevel(congestion)
                .averageSpeed(speed)
                .status(status)
                .incidents(incidents)
                .timestamp(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("Error parsing traffic info: {}", e.getMessage());
            throw new RuntimeException("Failed to parse traffic info", e);
        }
    }
    
    private double extractCongestion(Map<String, Object> info) {
        if (info.containsKey("congestion")) {
            return ((Number) info.get("congestion")).doubleValue();
        }
        return 0.5; // 기본값
    }
    
    private int extractSpeed(Map<String, Object> info) {
        if (info.containsKey("speed")) {
            return ((Number) info.get("speed")).intValue();
        }
        return 30; // 기본값 (km/h)
    }
    
    private String determineCongestionStatus(double congestion) {
        if (congestion < 0.3) return "원활";
        if (congestion < 0.7) return "보통";
        return "혼잡";
    }
    
    private List<String> extractIncidents(Map<String, Object> info) {
        List<String> incidents = new ArrayList<>();
        if (info.containsKey("accidents")) {
            // Use type checking before casting
            Object accidentsObj = info.get("accidents");
            if (accidentsObj instanceof List<?>) {
                List<?> accidentsList = (List<?>) accidentsObj;
                for (Object accidentObj : accidentsList) {
                    if (accidentObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> accident = (Map<String, Object>) accidentObj;
                        if (accident.containsKey("description")) {
                            incidents.add(String.valueOf(accident.get("description")));
                        }
                    }
                }
            }
        }
        return incidents;
    }
    
    private TrafficStatus createDefaultTrafficStatus(Location location) {
        return TrafficStatus.builder()
            .location(location)
            .congestionLevel(0.5)
            .averageSpeed(30)
            .status("보통")
            .incidents(Collections.emptyList())
            .timestamp(LocalDateTime.now())
            .build();
    }
}
