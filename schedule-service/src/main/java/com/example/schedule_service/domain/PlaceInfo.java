package com.example.TripSpring.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceInfo {
   private String id;
   private String name;
   private String type; // 장소 유형 (마트, 서점 등)
   private String address;
   private double latitude;
   private double longitude;
   private String phone;
   private Map<String, String> openHours; // 요일별 영업시간
   private double rating; // 평점 (0-5)
   private double crowdLevel; // 혼잡도 (0-1)
   private int estimatedDuration ;
   private int popularityScore; // 인기도 점수
}