package com.example.schedule_service.dto.response.navigation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TotalOptions {
   private RouteOptionSummary fastest;
   private RouteOptionSummary cheapest;
}