package com.example.navigation_service.dto.domain.route;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import com.example.common.dto.domain.route.TransportMode;

@Data
@Builder
public class RouteSegment {
    private GeoPoint startPoint;
    private GeoPoint endPoint;
    private List<GeoPoint> path;
    private TransportMode transportMode;
    private RouteMetrics metrics;
}