// src/main/java/com/example/TripSpring/dto/transport/TransitPoint.java
package com.example.navigation_service.dto.transport;

import com.example.navigation_service.dto.domain.route.GeoPoint;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TransitPoint {
    private GeoPoint location;
    private String name;
    private TransitType type;
    private LocalDateTime estimatedTime;
    private List<TransitLine> availableLines;
}
