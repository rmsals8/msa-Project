package com.example.auth_service.schedule_service.dto.response.navigation;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RouteOptionsResponse {
   private List<com.example.auth_service.schedule_service.dto.response.navigation.RouteSegment> segments;
   private TotalOptions totalOptions;
}