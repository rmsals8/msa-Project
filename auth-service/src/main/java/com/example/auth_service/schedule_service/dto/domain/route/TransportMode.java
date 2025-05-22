package com.example.auth_service.schedule_service.dto.domain.route;

public enum TransportMode {
    WALK,
    BUS,
    SUBWAY,
    TAXI;

    public boolean isPublicTransit() {
        return this == BUS || this == SUBWAY;
    }
}