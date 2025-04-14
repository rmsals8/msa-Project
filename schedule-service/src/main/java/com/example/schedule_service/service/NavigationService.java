package com.example.TripSpring.service;

import com.example.TripSpring.dto.domain.Location;
import com.example.TripSpring.dto.domain.NavigationSession;
import com.example.TripSpring.dto.request.LocationUpdate;
import com.example.TripSpring.dto.request.StartNavigationRequest;
import com.example.TripSpring.dto.response.NavigationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {

    private final Map<String, NavigationSession> activeSessions = new ConcurrentHashMap<>();
    public NavigationStatus startNavigation(StartNavigationRequest request) {
        try {
            String navigationId = UUID.randomUUID().toString();
            Location currentLoc = convertRequestLocation(request.getCurrentLocation());
            Location destLoc = convertRequestLocation(request.getDestination());
            
            return startNavigation(navigationId, currentLoc, destLoc);
        } catch (Exception e) {
            log.error("Failed to start navigation: {}", e.getMessage());
            throw new RuntimeException("Failed to start navigation", e);
        }
    }

    private Location convertRequestLocation(StartNavigationRequest.Location reqLocation) {
        return new Location(
            reqLocation.getLatitude(),
            reqLocation.getLongitude(),
            reqLocation.getName()
        );
    }
    public NavigationStatus startNavigation(String navigationId, Location currentLocation, Location destination) {
        try {
            NavigationSession session = new NavigationSession(navigationId, currentLocation, destination);
            activeSessions.put(navigationId, session);

            return NavigationStatus.builder()
                .navigationId(navigationId)
                .status(NavigationStatus.Status.STARTED)
                .currentLocation(currentLocation)
                .nextWaypoint(destination)
                .remainingDistance(0)  // 초기값
                .remainingTime(0)      // 초기값
                .rerouting(false)
                .alerts(new ArrayList<>())
                .build();
        } catch (Exception e) {
            log.error("Failed to start navigation: {}", e.getMessage());
            throw new RuntimeException("Failed to start navigation", e);
        }
    }

        public NavigationStatus updateLocation(String navigationId, LocationUpdate update) {
        try {
            NavigationSession session = activeSessions.get(navigationId);
            if (session == null) {
                throw new IllegalStateException("Navigation session not found: " + navigationId);
            }

            session.updateCurrentLocation(update);
            return createNavigationStatusResponse(session);
        } catch (Exception e) {
            log.error("Failed to update location: {}", e.getMessage());
            throw new RuntimeException("Failed to update location", e);
        }
    }

    public NavigationStatus getStatus(String navigationId) {
        NavigationSession session = activeSessions.get(navigationId);
        if (session == null) {
            throw new IllegalStateException("Navigation session not found: " + navigationId);
        }
        return createNavigationStatusResponse(session);
    }

    private NavigationStatus createNavigationStatusResponse(NavigationSession session) {
        return NavigationStatus.builder()
            .navigationId(session.getNavigationId())
            .currentLocation(session.getCurrentLocation())
            .status(session.getStatus())
            .remainingDistance((int)session.getTotalDistance())
            .remainingTime(session.getTotalTime())
            .build();
    }

    public void stopNavigation(String navigationId) {
        NavigationSession session = activeSessions.remove(navigationId);
        if (session == null) {
            throw new IllegalStateException("Navigation session not found: " + navigationId);
        }
    }
}