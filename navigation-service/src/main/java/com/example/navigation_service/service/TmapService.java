package com.example.navigation_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.example.navigation_service.dto.domain.Location;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmapService {
    private final RestTemplate restTemplate;

    @Value("${tmap.api.key}")
    private final String apiKey;

    private final String baseUrl = "https://apis.openapi.sk.com";

    public Map<String, Object> getDetailedRoute(
            double startLat, double startLon,
            double endLat, double endLon,
            String mode) {
        try {
            String url;
            if ("WALK".equals(mode)) {
                url = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";
            } else {
                url = "https://apis.openapi.sk.com/tmap/routes?version=1";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appKey", apiKey);

            // T-map API 필수 파라미터 추가
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("startX", String.format("%.7f", startLon));
            requestBody.put("startY", String.format("%.7f", startLat));
            requestBody.put("endX", String.format("%.7f", endLon));
            requestBody.put("endY", String.format("%.7f", endLat));
            requestBody.put("reqCoordType", "WGS84GEO");
            requestBody.put("resCoordType", "WGS84GEO");
            requestBody.put("startName", URLEncoder.encode("출발지", "UTF-8"));
            requestBody.put("endName", URLEncoder.encode("도착지", "UTF-8"));

            // 자동차 경로일 경우 추가 파라미터
            if (!"WALK".equals(mode)) {
                requestBody.put("searchOption", "0"); // 최적 경로
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return response.getBody();
        } catch (Exception e) {
            log.error("T-map API error: {}", e.getMessage());
            throw new RuntimeException("Failed to get route from T-map API", e);
        }
    }

    public String getRealTimeTraffic(double lat, double lon) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/tmap/traffic/realtime")
                    .queryParam("appKey", apiKey)
                    .queryParam("lat", String.format("%.7f", lat))
                    .queryParam("lon", String.format("%.7f", lon))
                    .queryParam("radius", "1")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appKey", apiKey);

            log.debug("Requesting traffic info - URL: {}", url);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.debug("Traffic API Response: {}", response.getBody());
                return response.getBody();
            }
            return createDefaultTrafficInfo();
        } catch (Exception e) {
            log.error("Error getting real-time traffic: {}", e.getMessage());
            return createDefaultTrafficInfo();
        }
    }

    public String getWalkingRoute(Double startLat, Double startLon, Double endLat, Double endLon) {
        try {
            String url = baseUrl + "/tmap/routes/pedestrian";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appKey", apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("startX", String.format("%.7f", startLon));
            requestBody.put("startY", String.format("%.7f", startLat));
            requestBody.put("endX", String.format("%.7f", endLon));
            requestBody.put("endY", String.format("%.7f", endLat));
            requestBody.put("reqCoordType", "WGS84GEO");
            requestBody.put("resCoordType", "WGS84GEO");
            requestBody.put("startName", URLEncoder.encode("출발지", StandardCharsets.UTF_8));
            requestBody.put("endName", URLEncoder.encode("도착지", StandardCharsets.UTF_8));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            throw new RuntimeException("Failed to get walking route");
        } catch (Exception e) {
            log.error("Failed to get walking route from T-map API: {}", e.getMessage());
            throw new RuntimeException("Failed to get walking route", e);
        }
    }

    // TmapService.java
    public String getTrafficInfo(Double lat, Double lon) {
        String url = "https://apis.openapi.sk.com/tmap/traffic";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("appKey", apiKey);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("version", "1")
                .queryParam("centerLat", lat)
                .queryParam("centerLon", lon)
                .queryParam("reqCoordType", "WGS84GEO")
                .queryParam("zoomLevel", "14")
                .queryParam("trafficType", "AUTO");

        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class);

        return response.getBody();
    }

    private String createDefaultTrafficInfo() {
        try {
            Map<String, Object> defaultInfo = new HashMap<>();
            defaultInfo.put("trafficInfo", Map.of(
                    "status", "OK",
                    "estimatedTime", 0,
                    "congestion", 0.5));
            return new org.json.JSONObject(defaultInfo).toString();
        } catch (Exception e) {
            log.error("Error creating default traffic info", e);
            return "{}";
        }
    }

    public String getRoute(Location start, Location end) {
        String url = "https://apis.openapi.sk.com/tmap/routes?version=1";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("appKey", apiKey);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("startX", String.valueOf(start.getLongitude()));
        params.add("startY", String.valueOf(start.getLatitude()));
        params.add("endX", String.valueOf(end.getLongitude()));
        params.add("endY", String.valueOf(end.getLatitude()));
        params.add("reqCoordType", "WGS84GEO");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        return restTemplate.postForObject(url, entity, String.class);
    }

    public String getTransitRoute(Double startLat, Double startLon, Double endLat, Double endLon) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appKey", apiKey); // apiKey 설정

            String url = baseUrl + "/tmap/routes/transit";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("startX", String.format("%.7f", startLon));
            requestBody.put("startY", String.format("%.7f", startLat));
            requestBody.put("endX", String.format("%.7f", endLon));
            requestBody.put("endY", String.format("%.7f", endLat));
            requestBody.put("reqCoordType", "WGS84GEO");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get transit route", e);
            throw new RuntimeException("Failed to get transit route");
        }
    }

    public String getDrivingRoute(Double startLat, Double startLon, Double endLat, Double endLon) {
        try {
            validateCoordinates(startLat, startLon);
            validateCoordinates(endLat, endLon);

            String url = baseUrl + "/tmap/routes";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appKey", apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("startX", String.format("%.7f", startLon));
            requestBody.put("startY", String.format("%.7f", startLat));
            requestBody.put("endX", String.format("%.7f", endLon));
            requestBody.put("endY", String.format("%.7f", endLat));
            requestBody.put("reqCoordType", "WGS84GEO");
            requestBody.put("resCoordType", "WGS84GEO");
            requestBody.put("searchOption", "0");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to get driving route information");
            }
        } catch (Exception e) {
            log.error("Failed to get driving route from T-map API: {}", e.getMessage());
            throw new RuntimeException("Failed to get driving route information: " + e.getMessage());
        }
    }

    private void validateCoordinates(Double lat, Double lon) {
        if (lat == null || lon == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Invalid latitude: " + lat);
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Invalid longitude: " + lon);
        }
    }
}