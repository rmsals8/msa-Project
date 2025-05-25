package com.example.auth_service.schedule_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.example.auth_service.schedule_service.dto.domain.route.GeoPoint;
import com.example.auth_service.schedule_service.dto.domain.route.TransportMode;
import com.example.auth_service.schedule_service.dto.request.route.RouteRecommendationRequest;
import com.example.auth_service.schedule_service.dto.request.route.RouteRecommendationRequest.OptimizedSchedule;
import com.example.auth_service.schedule_service.dto.response.route.NavigationPointResponse;
import com.example.auth_service.schedule_service.dto.response.route.RouteSegmentDetailResponse;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.List;
import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteSegmentAnalyzer {
    private final TmapService2 tmapService;
    private final CrowdLevelAnalyzer crowdLevelAnalyzer;
    // 이동수단 선택을 위한 거리 기준
    private static final double WALK_DISTANCE_THRESHOLD = 0.8; // km
    private static final double TRANSIT_DISTANCE_THRESHOLD = 3.0; // km

    private RouteSegmentDetailResponse createDefaultSegment(
            RouteRecommendationRequest.OptimizedSchedule from,
            RouteRecommendationRequest.OptimizedSchedule to) {
        var segment = new RouteSegmentDetailResponse();
        segment.setFromLocation(from.getName());
        segment.setToLocation(to.getName());

        double distance = calculateDistance(
                from.getLocation().getLatitude(),
                from.getLocation().getLongitude(),
                to.getLocation().getLatitude(),
                to.getLocation().getLongitude());

        segment.setDistance(distance);
        segment.setDuration((int) (distance * 15)); // 대략적인 소요시간 계산 (15분/km)
        segment.setTransportMode(distance <= 0.8 ? TransportMode.WALK : TransportMode.SUBWAY);
        segment.setPath(List.of(
                new GeoPoint(from.getLocation().getLatitude(), from.getLocation().getLongitude()),
                new GeoPoint(to.getLocation().getLatitude(), to.getLocation().getLongitude())));
        segment.setTurnByTurn(new ArrayList<>());

        return segment;
    }

    public List<RouteSegmentDetailResponse> analyzeSegments(List<OptimizedSchedule> schedules) {
        List<RouteSegmentDetailResponse> segments = new ArrayList<>();

        for (int i = 0; i < schedules.size() - 1; i++) {
            var current = schedules.get(i);
            var next = schedules.get(i + 1);

            try {
                RouteSegmentDetailResponse segment = analyzeSegment(current, next);
                segments.add(segment);
            } catch (Exception e) {
                log.error("Error analyzing segment: {}", e.getMessage());
                segments.add(createDefaultSegment(current, next));
            }
        }

        return segments;
    }

    private RouteSegmentDetailResponse analyzeSegment(OptimizedSchedule current, OptimizedSchedule next) {
        var segment = new RouteSegmentDetailResponse();
        segment.setFromLocation(current.getName());
        segment.setToLocation(next.getName());

        // 직선거리 계산
        double directDistance = calculateDistance(
                current.getLocation().getLatitude(),
                current.getLocation().getLongitude(),
                next.getLocation().getLatitude(),
                next.getLocation().getLongitude());

        // 현재 시간의 혼잡도 체크
        double crowdLevel = crowdLevelAnalyzer.analyzeCrowdLevel(current.getEndTime());

        // 이동수단 결정
        TransportMode recommendedMode = determineTransportMode(
                directDistance,
                crowdLevel,
                current.getEndTime().getHour());

        // API 호출 및 경로 정보 설정
        String routeResponse = getRouteResponse(current, next, recommendedMode);
        mapRouteInfoToSegment(routeResponse, segment, recommendedMode);

        return segment;
    }

    private TransportMode determineTransportMode(double distance, double crowdLevel, int hour) {
        // 러시아워 체크 (오전 8-10시, 오후 6-8시)
        boolean isRushHour = (hour >= 8 && hour <= 10) || (hour >= 18 && hour <= 20);

        if (distance <= WALK_DISTANCE_THRESHOLD) {
            return TransportMode.WALK;
        } else if (distance <= TRANSIT_DISTANCE_THRESHOLD) {
            if (isRushHour) {
                // 러시아워에는 지하철 우선
                return TransportMode.SUBWAY;
            } else {
                return TransportMode.BUS;
            }
        } else {
            if (isRushHour && crowdLevel > 0.7) {
                // 러시아워 + 높은 혼잡도 = 지하철 권장
                return TransportMode.SUBWAY;
            } else {
                return TransportMode.TAXI;
            }
        }
    }

    private String getRouteResponse(OptimizedSchedule current, OptimizedSchedule next, TransportMode mode) {
        switch (mode) {
            case WALK:
                return tmapService.getWalkingRoute(
                        current.getLocation().getLatitude(),
                        current.getLocation().getLongitude(),
                        next.getLocation().getLatitude(),
                        next.getLocation().getLongitude());
            case BUS:
            case SUBWAY:
                return tmapService.getTransitRoute(
                        current.getLocation().getLatitude(),
                        current.getLocation().getLongitude(),
                        next.getLocation().getLatitude(),
                        next.getLocation().getLongitude());
            case TAXI:
                return tmapService.getDrivingRoute(
                        current.getLocation().getLatitude(),
                        current.getLocation().getLongitude(),
                        next.getLocation().getLatitude(),
                        next.getLocation().getLongitude());
            default:
                throw new IllegalArgumentException("Unsupported transport mode: " + mode);
        }
    }

// RouteSegmentAnalyzer.java - mapRouteInfoToSegment 메서드 수정

    private void mapRouteInfoToSegment(
            String routeResponse,
            RouteSegmentDetailResponse segment,
            TransportMode mode) {

        try {
            JSONObject json = new JSONObject(routeResponse);
            JSONArray features = json.getJSONArray("features");

            List<NavigationPointResponse> navigationPoints = new ArrayList<>();
            List<GeoPoint> pathPoints = new ArrayList<>();

            // 총 거리와 예상 소요시간 추출
            JSONObject properties = features.getJSONObject(0).getJSONObject("properties");
            
            double distanceMeters = properties.getDouble("totalDistance");
            double distanceKm = distanceMeters / 1000.0;
            segment.setDistance(distanceKm);

            // **시간 단위 정규화 및 검증**
            int apiTimeValue = properties.getInt("totalTime");
            int durationMinutes;
            
            // T-map API는 초 단위로 반환하므로 분으로 변환
            if (apiTimeValue > 1000) { // 1000초 이상이면 초 단위로 판단
                durationMinutes = Math.max(1, apiTimeValue / 60);
            } else { // 작은 값이면 이미 분 단위로 판단
                durationMinutes = Math.max(1, apiTimeValue);
            }
            
            // **거리 기반 합리성 검증**
            int maxReasonableTime = calculateMaxReasonableTime(distanceKm, mode);
            int minReasonableTime = calculateMinReasonableTime(distanceKm, mode);
            
            if (durationMinutes > maxReasonableTime) {
                log.warn("Duration too high: {} min for {} km, adjusting to {} min", 
                        durationMinutes, distanceKm, maxReasonableTime);
                durationMinutes = maxReasonableTime;
            } else if (durationMinutes < minReasonableTime) {
                log.warn("Duration too low: {} min for {} km, adjusting to {} min", 
                        durationMinutes, distanceKm, minReasonableTime);
                durationMinutes = minReasonableTime;
            }
            
            segment.setDuration(durationMinutes);

            // 경로 포인트와 안내 정보 추출 (기존 코드)
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                String type = feature.getString("type");

                if (type.equals("Point")) {
                    JSONObject point = feature.getJSONObject("geometry");
                    JSONArray coord = point.getJSONArray("coordinates");
                    String description = feature.getJSONObject("properties")
                            .optString("description", "직진");

                    navigationPoints.add(new NavigationPointResponse(
                            description,
                            new GeoPoint(coord.getDouble(1), coord.getDouble(0))));

                } else if (type.equals("LineString")) {
                    JSONArray coordinates = feature.getJSONObject("geometry")
                            .getJSONArray("coordinates");

                    for (int j = 0; j < coordinates.length(); j++) {
                        JSONArray coord = coordinates.getJSONArray(j);
                        pathPoints.add(new GeoPoint(
                                coord.getDouble(1),
                                coord.getDouble(0)));
                    }
                }
            }

            segment.setTransportMode(mode);
            segment.setPath(pathPoints);
            segment.setTurnByTurn(navigationPoints);

            log.info("Processed route segment: {}km, {}min, mode={}", 
                    distanceKm, durationMinutes, mode);

        } catch (Exception e) {
            log.error("Error parsing route response: {}", e.getMessage());
            
            // 파싱 실패시 거리 기반 기본값 설정
            double fallbackDistance = segment.getDistance() > 0 ? segment.getDistance() : 1.0;
            int fallbackDuration = calculateMaxReasonableTime(fallbackDistance, mode);
            segment.setDuration(fallbackDuration);
            
            throw new RuntimeException("Failed to parse route information");
        }
    }

    /**
     * 교통수단과 거리에 따른 최대 합리적 시간 계산
     */
    private int calculateMaxReasonableTime(double distanceKm, TransportMode mode) {
        double minSpeed = switch (mode) {
            case WALK -> 3.0;      // 최소 3km/h
            case BUS -> 10.0;      // 최소 10km/h (교통체증 고려)
            case SUBWAY -> 25.0;   // 최소 25km/h
            case TAXI -> 15.0;     // 최소 15km/h (교통체증 고려)
        };
        
        return Math.max(5, (int) Math.ceil(distanceKm / minSpeed * 60));
    }

    /**
     * 교통수단과 거리에 따른 최소 합리적 시간 계산
     */
    private int calculateMinReasonableTime(double distanceKm, TransportMode mode) {
        double maxSpeed = switch (mode) {
            case WALK -> 6.0;      // 최대 6km/h
            case BUS -> 50.0;      // 최대 50km/h
            case SUBWAY -> 80.0;   // 최대 80km/h
            case TAXI -> 80.0;     // 최대 80km/h
        };
        
        return Math.max(1, (int) Math.ceil(distanceKm / maxSpeed * 60));
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구의 반지름 (km)
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}