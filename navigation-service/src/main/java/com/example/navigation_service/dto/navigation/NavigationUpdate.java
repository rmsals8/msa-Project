//src/main/java/com/example/TripSpring/dto/navigation/NavigationUpdate.java
package com.example.navigation_service.dto.navigation;

import com.example.common.dto.domain.Location;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class NavigationUpdate {
    private String navigationId;
    private Location currentLocation;
    private double speed;
    private double heading;
    private LocalDateTime timestamp;
}
