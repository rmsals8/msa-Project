//src/main/java/com/example/TripSpring/dto/route/RouteSegmentDetail.java
package com.example.auth_service.navigation_service.dto.route;

import com.example.auth_service.navigation_service.dto.domain.route.GeoPoint;
import com.example.auth_service.common.dto.domain.Location;
import com.example.auth_service.common.dto.domain.route.TransportMode;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RouteSegmentDetail {
    private String segmentId;
    private String startLocationName;
    private String endLocationName;
    private Location startLocation; // 추가
    private Location endLocation; // 추가
    private TransportMode mode;
    private List<GeoPoint> path;
    private List<RouteStep> steps;
    private double distance;
    private int duration;
    private double congestion;
    private String instruction;
}