//src/main/java/com/example/TripSpring/dto/route/CompleteRoute.java
package com.example.schedule_service.dto.route;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

import com.example.schedule_service.dto.response.RouteOption;

@Data
@Builder
public class CompleteRoute {
    private String routeId;
    private List<RouteOption> options;
    private Map<String, Object> metadata;
    private String recommendationType;  // FASTEST, CHEAPEST, BALANCED 등
    private double score;
    private List<String> highlights;    // 경로의 주요 특징
}