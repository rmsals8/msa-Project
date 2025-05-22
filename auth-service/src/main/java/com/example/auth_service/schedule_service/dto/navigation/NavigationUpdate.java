//src/main/java/com/example/TripSpring/dto/navigation/NavigationUpdate.java
package com.example.auth_service.schedule_service.dto.navigation;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

import com.example.auth_service.schedule_service.dto.domain.Location;

@Data
@Builder
public class NavigationUpdate {
    private String navigationId;
    private Location currentLocation;
    private double speed;
    private double heading;
    private LocalDateTime timestamp;
}
