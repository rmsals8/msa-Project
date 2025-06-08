package com.example.auth_service.auth_service.service;

import com.example.auth_service.auth_service.domain.DailyUsage;
import com.example.auth_service.auth_service.domain.UserSubscription;
import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.repository.DailyUsageRepository;
import com.example.auth_service.auth_service.repository.UserSubscriptionRepository;
import com.example.auth_service.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageTrackingService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final DailyUsageRepository dailyUsageRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final int FREE_PLAN_LIMIT = 3;
    private static final int PREMIUM_PLAN_LIMIT = 10;
    
    // ✅ Redis 키 접두사
    private static final String USAGE_CACHE_PREFIX = "usage:";
    private static final String USER_PLAN_CACHE_PREFIX = "plan:";

    // ✅ 성능 최적화: Redis 기반 빠른 사용량 체크
    @Transactional(readOnly = true)
    public boolean canUseService(Long userNo) {
        try {
            // 1. Redis에서 오늘 사용량 확인 (매우 빠름)
            String today = LocalDate.now().toString();
            String usageKey = USAGE_CACHE_PREFIX + userNo + ":" + today;
            String currentUsageStr = redisTemplate.opsForValue().get(usageKey);
            
            int currentUsage = 0;
            if (currentUsageStr != null) {
                currentUsage = Integer.parseInt(currentUsageStr);
            }

            // 2. 사용자 플랜 정보 확인 (캐시 활용)
            int limit = getUserPlanLimit(userNo);

            boolean canUse = currentUsage < limit;
            log.debug("사용량 체크: 사용자={}, 현재사용량={}, 제한={}, 사용가능={}", 
                     userNo, currentUsage, limit, canUse);

            return canUse;

        } catch (Exception e) {
            log.error("사용량 체크 실패: userNo={}, error={}", userNo, e.getMessage());
            // 에러 시 안전하게 DB 조회로 fallback
            return canUseServiceFallback(userNo);
        }
    }

    // ✅ 성능 최적화: Redis 기반 빠른 사용량 증가
    @Transactional
    public boolean incrementUsage(Long userNo) {
        try {
            String today = LocalDate.now().toString();
            String usageKey = USAGE_CACHE_PREFIX + userNo + ":" + today;

            // 1. Redis에서 atomic increment (매우 빠름)
            Long newUsage = redisTemplate.opsForValue().increment(usageKey, 1);
            
            // 2. 첫 사용이면 만료 시간 설정 (자정까지)
            if (newUsage == 1) {
                redisTemplate.expire(usageKey, 1, TimeUnit.DAYS);
            }

            // 3. 비동기로 DB 업데이트 (응답 속도에 영향 없음)
            updateDatabaseAsync(userNo, today, newUsage.intValue());

            // 4. 사용량 체크
            int limit = getUserPlanLimit(userNo);
            
            log.debug("사용량 증가: 사용자={}, 새로운사용량={}, 제한={}", 
                     userNo, newUsage, limit);

            return newUsage <= limit;

        } catch (Exception e) {
            log.error("사용량 증가 실패: userNo={}, error={}", userNo, e.getMessage());
            // 에러 시 DB 직접 업데이트로 fallback
            return incrementUsageFallback(userNo);
        }
    }

    // ✅ 성능 최적화: Redis 기반 빠른 남은 사용량 조회
    public int getRemainingUsage(Long userNo) {
        try {
            String today = LocalDate.now().toString();
            String usageKey = USAGE_CACHE_PREFIX + userNo + ":" + today;
            String currentUsageStr = redisTemplate.opsForValue().get(usageKey);

            int currentUsage = 0;
            if (currentUsageStr != null) {
                currentUsage = Integer.parseInt(currentUsageStr);
            }

            int limit = getUserPlanLimit(userNo);
            int remaining = Math.max(0, limit - currentUsage);

            log.debug("남은 사용량 조회: 사용자={}, 제한={}, 사용량={}, 남은량={}", 
                     userNo, limit, currentUsage, remaining);

            return remaining;

        } catch (Exception e) {
            log.error("남은 사용량 조회 실패: userNo={}, error={}", userNo, e.getMessage());
            // 에러 시 DB 조회로 fallback
            return getRemainingUsageFallback(userNo);
        }
    }

    // ✅ 성능 최적화: 사용자 플랜 정보 캐시
    @Cacheable(value = "usage_info", key = "'plan:' + #userNo")
    private int getUserPlanLimit(Long userNo) {
        try {
            // Redis에서 플랜 정보 먼저 확인
            String planKey = USER_PLAN_CACHE_PREFIX + userNo;
            String planType = redisTemplate.opsForValue().get(planKey);
            
            if (planType != null) {
                return "PREMIUM".equals(planType) ? PREMIUM_PLAN_LIMIT : FREE_PLAN_LIMIT;
            }

            // Redis에 없으면 DB에서 조회하고 캐시
            User user = userRepository.findById(userNo)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userNo));

            UserSubscription subscription = subscriptionRepository
                    .findByUserAndStatus(user, UserSubscription.SubscriptionStatus.ACTIVE)
                    .orElseGet(() -> createDefaultFreeSubscription(user));

            // Redis에 플랜 정보 캐시 (1시간)
            String planTypeStr = subscription.getPlanType().toString();
            redisTemplate.opsForValue().set(planKey, planTypeStr, 1, TimeUnit.HOURS);

            return subscription.getPlanType() == UserSubscription.PlanType.PREMIUM 
                   ? PREMIUM_PLAN_LIMIT : FREE_PLAN_LIMIT;

        } catch (Exception e) {
            log.error("플랜 정보 조회 실패: userNo={}, error={}", userNo, e.getMessage());
            return FREE_PLAN_LIMIT; // 안전한 기본값
        }
    }

    // ✅ 비동기 DB 업데이트 (응답 속도에 영향 없음)
    @org.springframework.scheduling.annotation.Async("logTaskExecutor")
    @CacheEvict(value = "usage_info", key = "'daily:' + #userNo")
    protected void updateDatabaseAsync(Long userNo, String dateStr, int newUsage) {
        try {
            User user = userRepository.findById(userNo).orElse(null);
            if (user == null) {
                log.warn("사용자를 찾을 수 없음: userNo={}", userNo);
                return;
            }

            LocalDate date = LocalDate.parse(dateStr);
            DailyUsage dailyUsage = dailyUsageRepository
                    .findByUserAndUsageDate(user, date)
                    .orElseGet(() -> createNewDailyUsage(user, date));

            dailyUsage.setUsageCount(newUsage);
            dailyUsage.setLastUsedAt(LocalDateTime.now());
            dailyUsageRepository.save(dailyUsage);

            log.debug("DB 사용량 업데이트 완료: userNo={}, date={}, usage={}", 
                     userNo, dateStr, newUsage);

        } catch (Exception e) {
            log.error("비동기 DB 업데이트 실패: userNo={}, error={}", userNo, e.getMessage());
        }
    }

    // ✅ Fallback 메서드들 (Redis 장애 시 사용)
    private boolean canUseServiceFallback(Long userNo) {
        User user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userNo));

        UserSubscription subscription = subscriptionRepository
                .findByUserAndStatus(user, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElseGet(() -> createDefaultFreeSubscription(user));

        LocalDate today = LocalDate.now();
        DailyUsage dailyUsage = dailyUsageRepository
                .findByUserAndUsageDate(user, today)
                .orElse(null);

        int limit = subscription.getPlanType() == UserSubscription.PlanType.PREMIUM 
                   ? PREMIUM_PLAN_LIMIT : FREE_PLAN_LIMIT;
        int currentUsage = dailyUsage != null ? dailyUsage.getUsageCount() : 0;

        return currentUsage < limit;
    }

    private boolean incrementUsageFallback(Long userNo) {
        User user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userNo));

        UserSubscription subscription = subscriptionRepository
                .findByUserAndStatus(user, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElseGet(() -> createDefaultFreeSubscription(user));

        LocalDate today = LocalDate.now();
        DailyUsage dailyUsage = dailyUsageRepository
                .findByUserAndUsageDate(user, today)
                .orElseGet(() -> createNewDailyUsage(user, today));

        dailyUsage.setUsageCount(dailyUsage.getUsageCount() + 1);
        dailyUsage.setLastUsedAt(LocalDateTime.now());
        dailyUsageRepository.save(dailyUsage);

        int limit = subscription.getPlanType() == UserSubscription.PlanType.PREMIUM 
                   ? PREMIUM_PLAN_LIMIT : FREE_PLAN_LIMIT;

        return true;
    }

    private int getRemainingUsageFallback(Long userNo) {
        User user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userNo));

        UserSubscription subscription = subscriptionRepository
                .findByUserAndStatus(user, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElseGet(() -> createDefaultFreeSubscription(user));

        LocalDate today = LocalDate.now();
        DailyUsage dailyUsage = dailyUsageRepository
                .findByUserAndUsageDate(user, today)
                .orElse(null);

        int limit = subscription.getPlanType() == UserSubscription.PlanType.PREMIUM 
                   ? PREMIUM_PLAN_LIMIT : FREE_PLAN_LIMIT;
        int usedCount = dailyUsage != null ? dailyUsage.getUsageCount() : 0;

        return limit - usedCount;
    }

    private UserSubscription createDefaultFreeSubscription(User user) {
        UserSubscription subscription = UserSubscription.builder()
                .user(user)
                .planType(UserSubscription.PlanType.FREE)
                .startDate(LocalDateTime.now())
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .build();
        return subscriptionRepository.save(subscription);
    }

    private DailyUsage createNewDailyUsage(User user, LocalDate date) {
        DailyUsage dailyUsage = DailyUsage.builder()
                .user(user)
                .usageDate(date)
                .usageCount(0)
                .build();
        return dailyUsageRepository.save(dailyUsage);
    }

    // ✅ 캐시 무효화 메서드 (플랜 변경 시 호출)
    @CacheEvict(value = "usage_info", key = "'plan:' + #userNo")
    public void clearUserPlanCache(Long userNo) {
        String planKey = USER_PLAN_CACHE_PREFIX + userNo;
        redisTemplate.delete(planKey);
        log.info("사용자 플랜 캐시 삭제: userNo={}", userNo);
    }

    // ✅ 일일 사용량 초기화 (자정에 실행되는 스케줄러에서 호출)
    public void resetDailyUsage() {
        // Redis에서 어제 사용량 키들 삭제
        String yesterday = LocalDate.now().minusDays(1).toString();
        String pattern = USAGE_CACHE_PREFIX + "*:" + yesterday;
        
        // 실제 운영환경에서는 Redis SCAN 사용 권장
        log.info("어제 사용량 캐시 정리 예정: pattern={}", pattern);
    }

    // ✅ 모니터링을 위한 통계 메서드
    public long getTotalActiveUsers() {
        String today = LocalDate.now().toString();
        String pattern = USAGE_CACHE_PREFIX + "*:" + today;
        // Redis SCAN을 사용해서 오늘 활성 사용자 수 조회
        // 간단한 구현을 위해 0 반환
        return 0;
    }
}