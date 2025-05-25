package com.example.auth_service.schedule_service.service;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.example.auth_service.schedule_service.dto.domain.Location;
import com.example.auth_service.schedule_service.dto.domain.TrafficInfo;
import org.springframework.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FirstMapService2 {
    @Value("${app.api.tmap}")
    private String apiKey;
    private final RestTemplate restTemplate;

    public FirstMapService2(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public TrafficInfo getTrafficInfo(Location start, Location end) {
        try {
            // T Map API 호출 시도
            String url = String.format(
                    "https://apis.openapi.sk.com/tmap/routes?startX=%f&startY=%f&endX=%f&endY=%f&appKey=%s",
                    start.getLongitude(), start.getLatitude(),
                    end.getLongitude(), end.getLatitude(), apiKey);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getBody() != null) {
                // API 응답 처리
                return extractTrafficInfo(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to get traffic info: {}", e.getMessage());
        }

        // API 호출 실패시 예상 값 반환
        return estimateTrafficInfo(start, end);
    }

    @SuppressWarnings("unchecked")
    private TrafficInfo extractTrafficInfo(Map<String, Object> responseBody) {
        try {
            Map<String, Object> features = (Map<String, Object>) ((List<?>) responseBody.get("features")).get(0);
            Map<String, Object> properties = (Map<String, Object>) features.get("properties");

            // **시간 단위 정규화 - T-map은 초 단위로 반환**
            double totalTimeSeconds = ((Number) properties.get("totalTime")).doubleValue();
            int totalTimeMinutes = Math.max(1, (int) Math.ceil(totalTimeSeconds / 60.0));
            
            double totalDistance = ((Number) properties.get("totalDistance")).doubleValue() / 1000.0; // meters to km

            // **비정상적인 시간 체크 및 보정**
            if (totalTimeMinutes > 180) { // 3시간 초과
                log.warn("API returned unrealistic time: {} minutes for distance: {} km", 
                        totalTimeMinutes, totalDistance);
                totalTimeMinutes = calculateReasonableTime(totalDistance);
            }

            log.info("Extracted traffic info: distance={}km, time={}min", totalDistance, totalTimeMinutes);
            return new TrafficInfo(1.0, totalTimeMinutes, totalDistance);
        } catch (Exception e) {
            log.error("Error extracting traffic info: {}", e.getMessage());
            return estimateTrafficInfo(null, null);
        }
    }

    /**
     * 거리 기반으로 합리적인 이동시간 계산
     */
    private int calculateReasonableTime(double distanceKm) {
        // 거리별 적절한 이동수단과 속도 결정
        if (distanceKm <= 0.5) {
            // 500m 이하: 도보 (4km/h)
            return Math.max(5, (int) Math.ceil(distanceKm / 4.0 * 60));
        } else if (distanceKm <= 2.0) {
            // 2km 이하: 도보 또는 자전거 (6km/h)
            return Math.max(10, (int) Math.ceil(distanceKm / 6.0 * 60));
        } else if (distanceKm <= 10.0) {
            // 10km 이하: 대중교통 (20km/h 평균)
            return Math.max(15, (int) Math.ceil(distanceKm / 20.0 * 60));
        } else if (distanceKm <= 30.0) {
            // 30km 이하: 자동차 (40km/h 평균)
            return Math.max(30, (int) Math.ceil(distanceKm / 40.0 * 60));
        } else {
            // 30km 초과: 고속 이동 (60km/h 평균)
            return Math.max(45, (int) Math.ceil(distanceKm / 60.0 * 60));
        }
    }

    // ✅ 수정된 estimateTrafficInfo 메소드
    private TrafficInfo estimateTrafficInfo(Location start, Location end) {
        if (start == null || end == null) {
            return new TrafficInfo(1.0, 15, 1.0); // 기본값
        }

        // 하버사인 공식으로 거리 계산
        double distance = calculateDistance(start, end);
        
        // ✅ 개선된 시간 계산 - calculateReasonableTime 사용
        int estimatedTime = calculateReasonableTime(distance);

        log.info("Estimated traffic info: distance={}km, time={}min", distance, estimatedTime);
        
        return new TrafficInfo(1.0, estimatedTime, distance);
    }

    private double calculateDistance(Location start, Location end) {
        final int R = 6371; // Earth's radius in kilometers

        double lat1 = Math.toRadians(start.getLatitude());
        double lon1 = Math.toRadians(start.getLongitude());
        double lat2 = Math.toRadians(end.getLatitude());
        double lon2 = Math.toRadians(end.getLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}