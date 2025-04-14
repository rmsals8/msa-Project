package com.example.TripSpring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.TripSpring.domain.PlaceInfo;
import com.example.TripSpring.dto.response.NearbyPlacesResponse;
import com.example.TripSpring.exception.PlaceSearchException;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyPlaceService {

    private final RestTemplate restTemplate;

    @Value("${app.api.kakao}")
    private String kakaoApiKey;

    private static final String KAKAO_LOCAL_API_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    public NearbyPlacesResponse findNearbyPlaces(double lat, double lon, String type, double radius) {
        try {
            // 1. 카카오 로컬 API 호출
            List<PlaceInfo> kakaoResults = searchKakaoPlaces(lat, lon, type, radius);

            // 2. 결과가 없으면 검색 결과 없음 상태로 반환
            if (kakaoResults.isEmpty()) {
                log.info("No results from Kakao API for {}", type);
                NearbyPlacesResponse response = new NearbyPlacesResponse(new ArrayList<>());
                response.setNoResults();
                return response;
            }

            return new NearbyPlacesResponse(kakaoResults);
        } catch (Exception e) {
            log.error("Failed to search nearby places", e);
            throw new PlaceSearchException("Failed to search nearby places: " + e.getMessage(), e);
        }
    }

    private List<PlaceInfo> searchKakaoPlaces(double lat, double lon, String type, double radius) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);

            String url = UriComponentsBuilder.fromHttpUrl(KAKAO_LOCAL_API_URL)
                    .queryParam("query", type)
                    .queryParam("y", lat)
                    .queryParam("x", lon)
                    .queryParam("radius", radius)
                    .queryParam("size", 15)
                    .build()
                    .toUriString();

            log.debug("Kakao API request URL: {}", url);

            // Use parameterized type for the response
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseKakaoResponse(response.getBody(), type);
            }

            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to call Kakao API", e);
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<PlaceInfo> parseKakaoResponse(Map<String, Object> response, String type) {
        List<PlaceInfo> results = new ArrayList<>();

        try {
            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");

            if (documents != null) {
                for (Map<String, Object> document : documents) {
                    PlaceInfo place = PlaceInfo.builder()
                            .id(document.get("id").toString())
                            .name(document.get("place_name").toString())
                            .type(type)
                            .address(document.get("address_name").toString())
                            .latitude(Double.parseDouble(document.get("y").toString()))
                            .longitude(Double.parseDouble(document.get("x").toString()))
                            .phone(document.getOrDefault("phone", "").toString())
                            .openHours(getDefaultOpenHours())
                            .rating(4.0) // 기본값
                            .crowdLevel(calculateCrowdLevel())
                            .build();

                    results.add(place);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing Kakao API response", e);
        }

        return results;
    }

    private Map<String, String> getDefaultOpenHours() {
        Map<String, String> openHours = new HashMap<>();
        openHours.put("monday", "09:00-21:00");
        openHours.put("tuesday", "09:00-21:00");
        openHours.put("wednesday", "09:00-21:00");
        openHours.put("thursday", "09:00-21:00");
        openHours.put("friday", "09:00-21:00");
        openHours.put("saturday", "10:00-20:00");
        openHours.put("sunday", "10:00-20:00");
        return openHours;
    }

    private double calculateCrowdLevel() {
        // 현재 시간을 기준으로 혼잡도 계산 (0.0 ~ 1.0)
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);

        // 피크 시간대 (점심, 저녁)
        if (hour >= 12 && hour <= 13 || hour >= 18 && hour <= 19) {
            return 0.7 + Math.random() * 0.3; // 0.7-1.0 (높은 혼잡도)
        }

        // 준 피크 시간대
        if (hour >= 11 && hour <= 14 || hour >= 17 && hour <= 20) {
            return 0.4 + Math.random() * 0.3; // 0.4-0.7 (중간 혼잡도)
        }

        // 그 외 시간대
        return Math.random() * 0.4; // 0.0-0.4 (낮은 혼잡도)
    }
}