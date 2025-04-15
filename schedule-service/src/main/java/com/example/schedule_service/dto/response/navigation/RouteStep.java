package com.example.schedule_service.dto.response.navigation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteStep {
   private String type;
   private String instruction;
   private int duration;
}