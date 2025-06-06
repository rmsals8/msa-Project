package com.example.auth_service.schedule_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.example.auth_service.schedule_service.dto.domain.Location;
import com.example.auth_service.schedule_service.dto.domain.Schedule;
import com.example.auth_service.schedule_service.dto.domain.ScheduleType;
import com.example.auth_service.schedule_service.dto.request.MultipleScheduleOptimizationRequest;
import com.example.auth_service.schedule_service.dto.response.MultipleOptimizeResponse;
import com.example.auth_service.schedule_service.dto.scheduler.OptimizeResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipleScheduleOptimizationService {
    private final ScheduleOptimizationService scheduleOptimizationService;

    public MultipleOptimizeResponse optimizeMultipleSchedules(MultipleScheduleOptimizationRequest request) {
        try {
            log.info("Multiple schedule optimization started with {} options", request.getOptions().size());

            List<MultipleOptimizeResponse.OptimizedOption> optimizedOptions = new ArrayList<>();

            // 각 옵션별로 최적화 수행
            for (MultipleScheduleOptimizationRequest.ScheduleOption option : request.getOptions()) {
                log.info("Processing option {}", option.getOptionId());

                // DTO를 도메인 객체로 변환
                List<Schedule> fixedSchedules = convertFixedSchedules(option.getFixedSchedules());
                List<Schedule> flexibleSchedules = convertFlexibleSchedules(option.getFlexibleSchedules());

                // 기존 최적화 서비스 호출
                OptimizeResponse result = scheduleOptimizationService.optimizeSchedule(fixedSchedules, flexibleSchedules);

                // 옵션 점수 계산
                MultipleOptimizeResponse.OptionScore score = calculateOptionScore(result);

                // 추천 메시지 생성
                String recommendation = generateRecommendation(score, result);

                MultipleOptimizeResponse.OptimizedOption optimizedOption = 
                    MultipleOptimizeResponse.OptimizedOption.builder()
                        .optionId(option.getOptionId())
                        .result(result)
                        .score(score)
                        .recommendation(recommendation)
                        .build();

                optimizedOptions.add(optimizedOption);
                log.info("Option {} processed with score: {}", option.getOptionId(), score.getTotalScore());
            }

            // 옵션들 비교 및 추천
            MultipleOptimizeResponse.ComparisonMetrics comparison = compareOptions(optimizedOptions);

            MultipleOptimizeResponse response = MultipleOptimizeResponse.builder()
                    .optimizedOptions(optimizedOptions)
                    .comparison(comparison)
                    .build();

            log.info("Multiple schedule optimization completed successfully");
            return response;

        } catch (Exception e) {
            log.error("Error in multiple schedule optimization", e);
            throw new RuntimeException("Failed to optimize multiple schedules: " + e.getMessage(), e);
        }
    }

    private List<Schedule> convertFixedSchedules(List<MultipleScheduleOptimizationRequest.FixedScheduleDTO> dtoList) {
        List<Schedule> schedules = new ArrayList<>();

        for (MultipleScheduleOptimizationRequest.FixedScheduleDTO dto : dtoList) {
            Location location = new Location(dto.getLatitude(), dto.getLongitude(), dto.getName());

            Schedule schedule = Schedule.builder()
                    .id(dto.getId())
                    .name(dto.getName())
                    .type(ScheduleType.FIXED)
                    .location(location)
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .priority(dto.getPriority())
                    .estimatedDuration(dto.getDuration())
                    .build();

            schedule.setLocationString(dto.getLocation());
            schedules.add(schedule);
        }

        return schedules;
    }

    private List<Schedule> convertFlexibleSchedules(List<MultipleScheduleOptimizationRequest.FlexibleScheduleDTO> dtoList) {
        List<Schedule> schedules = new ArrayList<>();

        for (MultipleScheduleOptimizationRequest.FlexibleScheduleDTO dto : dtoList) {
            Location defaultLocation = new Location(0.0, 0.0, dto.getName());

            Schedule schedule = Schedule.builder()
                    .id(dto.getId())
                    .name(dto.getName())
                    .type(ScheduleType.FLEXIBLE)
                    .location(defaultLocation)
                    .priority(dto.getPriority())
                    .estimatedDuration(dto.getDuration())
                    .build();

            schedules.add(schedule);
        }

        return schedules;
    }

    private MultipleOptimizeResponse.OptionScore calculateOptionScore(OptimizeResponse result) {
        // 시간 효율성 (짧을수록 좋음)
        double totalTimeHours = result.getMetrics().getTotalTime() / 60.0;
        double timeEfficiency = Math.max(0.1, 1.0 / Math.max(1, totalTimeHours));

        // 거리 효율성 (짧을수록 좋음)
        double distanceEfficiency = Math.max(0.1, 1.0 / Math.max(1, result.getMetrics().getTotalDistance()));

        // 비용 효율성 (적을수록 좋음) - 예상 택시비 기준
        double estimatedCost = result.getMetrics().getTotalDistance() * 1000; // 1km당 1000원 가정
        double costEfficiency = Math.max(0.1, 1.0 / Math.max(1, estimatedCost / 10000.0));

        // 종합 점수 (가중평균) - 정규화하여 0~100 점수로 변환
        double totalScore = ((timeEfficiency * 0.4) + (distanceEfficiency * 0.4) + (costEfficiency * 0.2)) * 100;

        // 등급 계산
        String grade = calculateGrade(totalScore);

        return MultipleOptimizeResponse.OptionScore.builder()
                .totalScore(Math.round(totalScore * 100.0) / 100.0) // 소수점 2자리까지
                .timeEfficiency(Math.round(timeEfficiency * 100.0) / 100.0)
                .distanceEfficiency(Math.round(distanceEfficiency * 100.0) / 100.0)
                .costEfficiency(Math.round(costEfficiency * 100.0) / 100.0)
                .grade(grade)
                .build();
    }

    private String calculateGrade(double score) {
        if (score >= 80) return "A";
        else if (score >= 70) return "B";
        else if (score >= 60) return "C";
        else if (score >= 50) return "D";
        else return "F";
    }

    private String generateRecommendation(MultipleOptimizeResponse.OptionScore score, OptimizeResponse result) {
        StringBuilder recommendation = new StringBuilder();

        // 등급에 따른 기본 추천
        switch (score.getGrade()) {
            case "A":
                recommendation.append("매우 효율적인 일정입니다. ");
                break;
            case "B":
                recommendation.append("좋은 일정입니다. ");
                break;
            case "C":
                recommendation.append("보통 수준의 일정입니다. ");
                break;
            case "D":
                recommendation.append("개선이 필요한 일정입니다. ");
                break;
            case "F":
                recommendation.append("비효율적인 일정입니다. ");
                break;
        }

        // 구체적인 개선 사항 제안
        if (score.getTimeEfficiency() < 0.5) {
            recommendation.append("이동 시간을 줄일 수 있는 방법을 고려해보세요. ");
        }

        if (score.getDistanceEfficiency() < 0.5) {
            recommendation.append("더 가까운 위치의 장소를 선택하면 좋겠습니다. ");
        }

        if (result.getMetrics().getTotalDistance() > 20) {
            recommendation.append("총 이동거리가 상당히 깁니다. ");
        }

        if (result.getMetrics().getTotalTime() > 180) { // 3시간 이상
            recommendation.append("총 소요시간이 길어 피로할 수 있습니다. ");
        }

        return recommendation.toString().trim();
    }

    private MultipleOptimizeResponse.ComparisonMetrics compareOptions(
            List<MultipleOptimizeResponse.OptimizedOption> options) {

        if (options.isEmpty()) {
            return MultipleOptimizeResponse.ComparisonMetrics.builder().build();
        }

        // 시간이 가장 좋은 옵션
        MultipleOptimizeResponse.OptimizedOption bestTimeOption = options.stream()
                .min(Comparator.comparing(opt -> opt.getResult().getMetrics().getTotalTime()))
                .orElse(null);

        // 거리가 가장 좋은 옵션
        MultipleOptimizeResponse.OptimizedOption bestDistanceOption = options.stream()
                .min(Comparator.comparing(opt -> opt.getResult().getMetrics().getTotalDistance()))
                .orElse(null);

        // 비용이 가장 좋은 옵션 (거리와 비례한다고 가정)
        MultipleOptimizeResponse.OptimizedOption bestCostOption = bestDistanceOption;

        // 종합 점수가 가장 좋은 옵션
        MultipleOptimizeResponse.OptimizedOption recommendedOption = options.stream()
                .max(Comparator.comparing(opt -> opt.getScore().getTotalScore()))
                .orElse(null);

        // 비교 요약 정보 생성
        MultipleOptimizeResponse.ComparisonSummary summary = generateComparisonSummary(options);

        return MultipleOptimizeResponse.ComparisonMetrics.builder()
                .bestTimeOption(bestTimeOption)
                .bestDistanceOption(bestDistanceOption)
                .bestCostOption(bestCostOption)
                .recommendedOption(recommendedOption)
                .summary(summary)
                .build();
    }

    private MultipleOptimizeResponse.ComparisonSummary generateComparisonSummary(
            List<MultipleOptimizeResponse.OptimizedOption> options) {

        if (options.isEmpty()) {
            return MultipleOptimizeResponse.ComparisonSummary.builder()
                    .totalOptionsAnalyzed(0)
                    .overallRecommendation("분석할 옵션이 없습니다.")
                    .build();
        }

        // 시간 분산 계산
        List<Integer> times = options.stream()
                .map(opt -> opt.getResult().getMetrics().getTotalTime())
                .toList();
        double timeVariance = calculateVariancePercentage(times);

        // 거리 분산 계산
        List<Double> distances = options.stream()
                .map(opt -> opt.getResult().getMetrics().getTotalDistance())
                .toList();
        double distanceVariance = calculateVariancePercentage(distances);

        // 전체 추천 메시지 생성
        String overallRecommendation = generateOverallRecommendation(options, timeVariance, distanceVariance);

        return MultipleOptimizeResponse.ComparisonSummary.builder()
                .timeVariancePercentage(Math.round(timeVariance * 100.0) / 100.0)
                .distanceVariancePercentage(Math.round(distanceVariance * 100.0) / 100.0)
                .totalOptionsAnalyzed(options.size())
                .overallRecommendation(overallRecommendation)
                .build();
    }

    private double calculateVariancePercentage(List<? extends Number> values) {
        if (values.size() <= 1) return 0.0;

        double mean = values.stream().mapToDouble(Number::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(Number::doubleValue)
                .map(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(0.0);

        return mean > 0 ? (Math.sqrt(variance) / mean) * 100 : 0.0;
    }

    private String generateOverallRecommendation(
            List<MultipleOptimizeResponse.OptimizedOption> options,
            double timeVariance,
            double distanceVariance) {

        StringBuilder recommendation = new StringBuilder();

        // 옵션들의 다양성 평가
        if (timeVariance < 10 && distanceVariance < 10) {
            recommendation.append("모든 옵션이 비슷한 효율성을 가지고 있어 어떤 것을 선택해도 좋습니다. ");
        } else if (timeVariance > 30 || distanceVariance > 30) {
            recommendation.append("옵션들 간의 차이가 크니 신중하게 선택하세요. ");
        }

        // 최고 점수 옵션 기준 추천
        MultipleOptimizeResponse.OptimizedOption best = options.stream()
                .max(Comparator.comparing(opt -> opt.getScore().getTotalScore()))
                .orElse(null);

        if (best != null) {
            recommendation.append(String.format("옵션 %d번을 가장 추천합니다. (점수: %.1f점)",
                    best.getOptionId(), best.getScore().getTotalScore()));
        }

        return recommendation.toString();
    }
}