package com.example.schedule_service.provider;

import com.example.schedule_service.dto.domain.PlaceInfo;

public interface PlaceSearchProvider {
    PlaceInfo searchPlace(String placeName, double lat, double lng);
}