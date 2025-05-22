package com.example.auth_service.navigation_service.dto.response.navigation;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import com.example.auth_service.common.dto.domain.route.TransportMode;

@Data
@Builder
public class TransportOption {
   private TransportMode transportMode;
   private List<RouteDetail> routes;
}