package com.example.auth_service.schedule_service.provider;

import com.example.auth_service.schedule_service.dto.domain.PlaceInfo;

public interface PlaceSearchProvider {
    PlaceInfo searchPlace(String placeName, double lat, double lng);
}