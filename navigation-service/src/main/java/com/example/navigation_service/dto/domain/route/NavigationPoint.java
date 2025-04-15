package com.example.navigation_service.dto.domain.route;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class NavigationPoint {
    private String instruction;
    private GeoPoint point;
}
