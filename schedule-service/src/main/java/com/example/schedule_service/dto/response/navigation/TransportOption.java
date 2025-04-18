package com.example.schedule_service.dto.response.navigation;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import com.example.schedule_service.dto.domain.route.TransportMode;

@Data
@Builder
public class TransportOption {
   private TransportMode transportMode;
   private List<RouteDetail> routes;
}