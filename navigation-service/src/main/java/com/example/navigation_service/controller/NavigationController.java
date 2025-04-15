package com.example.navigation_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.example.navigation_service.dto.domain.route.TransportMode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.navigation_service.dto.response.navigation.RouteSegment;
import com.example.navigation_service.dto.response.navigation.RouteStep;
import com.example.navigation_service.dto.request.StartNavigationRequest;
import com.example.navigation_service.dto.request.LocationUpdate;
import com.example.navigation_service.dto.response.NavigationStatus;
import com.example.navigation_service.dto.response.navigation.RouteOptionsResponse;
import com.example.navigation_service.dto.response.navigation.RouteDetail;
import com.example.navigation_service.dto.response.navigation.RouteOptionSummary;
import com.example.navigation_service.dto.response.navigation.TransportOption;
import com.example.navigation_service.dto.response.navigation.TotalOptions;
import com.example.navigation_service.service.NavigationService;
import com.example.navigation_service.exception.NavigationException;
import com.example.navigation_service.service.TmapService;
@Slf4j
@RestController
@RequestMapping("/api/v1/navigation")
@RequiredArgsConstructor
public class NavigationController {
    private final TmapService tmapService;
    private final NavigationService navigationService;

    @PostMapping("/start")
    public ResponseEntity<NavigationStatus> startNavigation(
        @RequestHeader(value = "X-API-KEY", required = true) String apiKey,
        @RequestBody StartNavigationRequest request
    ) {
        try {
            validateNavigationRequest(request);
            NavigationStatus status = navigationService.startNavigation(request);
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid navigation request: {}", e.getMessage());
            throw new NavigationException("Invalid request: " + e.getMessage());
        } catch (Exception e) {
            log.error("Navigation start failed", e);
            String errorMessage = (e.getMessage() != null && !e.getMessage().isEmpty()) 
                ? e.getMessage() 
                : "Internal server error occurred";
            throw new NavigationException("Failed to start navigation: " + errorMessage);
        }
    }
    
    private void validateNavigationRequest(StartNavigationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getCurrentLocation() == null) {
            throw new IllegalArgumentException("Current location is required");
        }
        if (request.getDestination() == null) {
            throw new IllegalArgumentException("Destination is required");
        }
    }
    @PutMapping("/{navigationId}/location")
    public ResponseEntity<NavigationStatus> updateLocation(
            @PathVariable String navigationId,
            @RequestBody LocationUpdate update) {
        try {
            log.debug("Updating location for navigation {}: {}", navigationId, update);
            NavigationStatus status = navigationService.updateLocation(navigationId, update);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to update location for navigation {}", navigationId, e);
            throw new NavigationException("Failed to update location: " + e.getMessage());
        }
    }

    @GetMapping("/{navigationId}/status")
    public ResponseEntity<NavigationStatus> getNavigationStatus(@PathVariable String navigationId) {
        try {
            log.debug("Fetching status for navigation {}", navigationId);
            NavigationStatus status = navigationService.getStatus(navigationId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get status for navigation {}", navigationId, e);
            throw new NavigationException("Failed to get navigation status: " + e.getMessage());
        }
    }

    @DeleteMapping("/{navigationId}")
    public ResponseEntity<Void> stopNavigation(@PathVariable String navigationId) {
        try {
            log.info("Stopping navigation {}", navigationId);
            navigationService.stopNavigation(navigationId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to stop navigation {}", navigationId, e);
            throw new NavigationException("Failed to stop navigation: " + e.getMessage());
        }
    }

    @GetMapping("/routes")
    public ResponseEntity<RouteOptionsResponse> getRoutes(
        @RequestParam double startLat,
        @RequestParam double startLon, 
        @RequestParam double endLat,
        @RequestParam double endLon
    ) {
        try {
            // 실제 T맵 API 호출하여 도보/자동차 경로 조회
            Map<String, Object> walkingRoute = tmapService.getDetailedRoute(
                startLat, startLon, endLat, endLon, "WALK");
            Map<String, Object> drivingRoute = tmapService.getDetailedRoute(
                startLat, startLon, endLat, endLon, "TAXI");
    
            List<RouteSegment> segments = new ArrayList<>();
    
            // 도보 경로 추가
            if (walkingRoute.containsKey("totalDistance")) {
                double walkDistance = (double) walkingRoute.get("totalDistance");
                int walkTime = (int) walkingRoute.get("totalTime");
                
                segments.add(RouteSegment.builder()
                    .fromLocation("출발지")
                    .toLocation("도착지")
                    .options(Collections.singletonList(
                        TransportOption.builder()
                            .transportMode(TransportMode.WALK)
                            .routes(Collections.singletonList(
                                RouteDetail.builder()
                                    .summary("도보 경로")
                                    .duration(walkTime)
                                    .distance(walkDistance / 1000.0) // m -> km
                                    .cost(0.0)
                                    .steps((List<RouteStep>) walkingRoute.get("turnByTurn"))
                                    .crowdedness("LOW")
                                    .build()
                            ))
                            .build()
                    ))
                    .build());
            }
    
            // 택시 경로 추가
            if (drivingRoute.containsKey("totalDistance")) {
                double driveDistance = (double) drivingRoute.get("totalDistance");
                int driveTime = (int) drivingRoute.get("totalTime");
                double taxiFare = calculateTaxiFare(driveDistance / 1000.0); // m -> km
                
                segments.add(RouteSegment.builder()
                    .fromLocation("출발지")
                    .toLocation("도착지")
                    .options(Collections.singletonList(
                        TransportOption.builder()
                            .transportMode(TransportMode.TAXI)
                            .routes(Collections.singletonList(
                                RouteDetail.builder()
                                    .summary("택시 경로")
                                    .duration(driveTime)
                                    .distance(driveDistance / 1000.0)
                                    .cost(taxiFare)
                                    .steps((List<RouteStep>) drivingRoute.get("turnByTurn"))
                                    .crowdedness(
                                        drivingRoute.containsKey("trafficInfo") ? 
                                        ((Map<String, Object>) drivingRoute.get("trafficInfo"))
                                            .get("status").toString() : "MODERATE"
                                    )
                                    .build()
                            ))
                            .build()
                    ))
                    .build());
            }
    
            // 전체 옵션 분석
            TotalOptions totalOptions = TotalOptions.builder()
                .fastest(findFastestOption(segments))
                .cheapest(findCheapestOption(segments))
                .build();
    
            return ResponseEntity.ok(
                RouteOptionsResponse.builder()
                    .segments(segments)
                    .totalOptions(totalOptions)
                    .build()
            );
    
        } catch (Exception e) {
            log.error("Route calculation failed", e);
            throw new NavigationException("경로 계산 중 오류가 발생했습니다");
        }
    }
    
  
private double calculateTaxiFare(double distanceKm) {
    double baseFare = 3800;  // 기본요금
    double ratePerKm = 1000; // km당 요금
    return baseFare + (distanceKm * ratePerKm);
}
 
private RouteOptionSummary findFastestOption(List<RouteSegment> segments) {
    // 가장 짧은 소요시간을 가진 경로 찾기
    RouteSegment fastestSegment = segments.stream()
        .min(Comparator.comparingInt(segment -> 
            segment.getOptions().get(0).getRoutes().get(0).getDuration()))
        .orElseThrow(() -> new NavigationException("경로를 찾을 수 없습니다"));
 
    RouteDetail fastestRoute = fastestSegment.getOptions().get(0).getRoutes().get(0);
    TransportMode fastestMode = fastestSegment.getOptions().get(0).getTransportMode();
 
    return RouteOptionSummary.builder()
        .duration(fastestRoute.getDuration())
        .cost(fastestRoute.getCost())
        .modes(Collections.singletonList(fastestMode.toString()))
        .build();
 }
 
 private RouteOptionSummary findCheapestOption(List<RouteSegment> segments) {
    // 가장 낮은 비용을 가진 경로 찾기 
    RouteSegment cheapestSegment = segments.stream()
        .min(Comparator.comparingDouble(segment -> 
            segment.getOptions().get(0).getRoutes().get(0).getCost()))
        .orElseThrow(() -> new NavigationException("경로를 찾을 수 없습니다"));
 
    RouteDetail cheapestRoute = cheapestSegment.getOptions().get(0).getRoutes().get(0);
    TransportMode cheapestMode = cheapestSegment.getOptions().get(0).getTransportMode();
 
    return RouteOptionSummary.builder()
        .duration(cheapestRoute.getDuration())
        .cost(cheapestRoute.getCost())
        .modes(Collections.singletonList(cheapestMode.toString()))
        .build();
 }
 @PutMapping("/{navigationId}/update")
 public ResponseEntity<NavigationStatus> updateNavigationLocation(
         @PathVariable String navigationId,
         @RequestBody LocationUpdate locationUpdate) {
     
     log.info("Received location update for navigation {}: lat={}, lon={}, speed={}, heading={}",
         navigationId,
         locationUpdate.getLatitude(),
         locationUpdate.getLongitude(),
         locationUpdate.getSpeed(),
         locationUpdate.getHeading()
     );
     
     try {
         NavigationStatus status = navigationService.updateLocation(
             navigationId, 
             locationUpdate
         );
         return ResponseEntity.ok(status);
     } catch (IllegalStateException e) {
         log.warn("Navigation session not found: {}", navigationId);
         return ResponseEntity.notFound().build();
     } catch (Exception e) {
         log.error("Error updating navigation: {}", e.getMessage());
         throw e;
     }
 }

}