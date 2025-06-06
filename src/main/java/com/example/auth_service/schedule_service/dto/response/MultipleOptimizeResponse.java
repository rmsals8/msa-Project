package com.example.auth_service.schedule_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.example.auth_service.schedule_service.dto.scheduler.OptimizeResponse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipleOptimizeResponse {
    private List<OptimizedOption> optimizedOptions;
    private ComparisonMetrics comparison;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizedOption {
        private Long optionId;
        private OptimizeResponse result;
        private OptionScore score;
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionScore {
        private double totalScore;
        private double timeEfficiency;
        private double distanceEfficiency;
        private double costEfficiency;
        private String grade;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonMetrics {
        private OptimizedOption bestTimeOption;
        private OptimizedOption bestDistanceOption;
        private OptimizedOption bestCostOption;
        private OptimizedOption recommendedOption;
        private ComparisonSummary summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonSummary {
        private double timeVariancePercentage;
        private double distanceVariancePercentage;
        private int totalOptionsAnalyzed;
        private String overallRecommendation;
    }
}