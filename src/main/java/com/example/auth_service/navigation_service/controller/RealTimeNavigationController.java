//src/main/java/com/example/TripSpring/controller/RealTimeNavigationController.java
package com.example.auth_service.navigation_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.auth_service.navigation_service.dto.navigation.NavigationResponse;
import com.example.auth_service.navigation_service.dto.navigation.NavigationUpdate;
import com.example.auth_service.navigation_service.service.navigation.RealTimeNavigationService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RealTimeNavigationController {
    private final RealTimeNavigationService navigationService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/navigation.start")
    @SendTo("/topic/navigation")
    public NavigationResponse startNavigation(String navigationId) {
        log.info("Starting navigation session: {}", navigationId);
        return navigationService.startNavigation(navigationId, null, null);
    }

    @MessageMapping("/navigation.update")
    public void updateLocation(NavigationUpdate update) {
        log.info("Received location update for navigation {}: {}",
                update.getNavigationId(), update);

        NavigationResponse response = navigationService.processUpdate(update);

        // 개별 세션에 대한 응답 전송
        messagingTemplate.convertAndSend(
                "/topic/navigation/" + update.getNavigationId(),
                response);
    }
}
