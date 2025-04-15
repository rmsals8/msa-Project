package com.example.navigation_service.dto.domain.route;

public enum TransportMode {
    WALK,
    BUS,
    SUBWAY,
    TAXI;
    
    public boolean isPublicTransit() {
        return this == BUS || this == SUBWAY;
    }
}