package com.example.auth_service.service;

import com.example.auth_service.domain.DailyUsage;
import com.example.auth_service.domain.UserSubscription;
import com.example.auth_service.domain.User;
import com.example.auth_service.repository.DailyUsageRepository;
import com.example.auth_service.repository.UserSubscriptionRepository;
import com.example.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageTrackingService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final DailyUsageRepository dailyUsageRepository;
    private final UserRepository userRepository;

    private static final int FREE_PLAN_LIMIT = 3;
    private static final int PREMIUM_PLAN_LIMIT = 10;

    @Transactional(readOnly = true)
    public boolean canUseService(Long userNo) {
        User user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userNo));

        // 구독 정보 확인
        UserSubscription subscription = subscriptionRepository
                .findByUserAndStatus(user, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElseGet(() -> createDefaultFreeSubscription(user));

        // 일일 사용량 확인
        LocalDate today = LocalDate.now();
        DailyUsage dailyUsage = dailyUsageRepository
                .findByUserAndUsageDate(user, today)
                .orElse(null);

        // 사용 제한 체크
        int limit = subscription.getPlanType() == UserSubscription.PlanType.PREMIUM ? PREMIUM_PLAN_LIMIT
                : FREE_PLAN_LIMIT;

        int currentUsage = dailyUsage != null ? dailyUsage.getUsageCount() : 0;
        return currentUsage < limit;
    }

    @Transactional
    public boolean incrementUsage(Long userNo) {
        User user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userNo));

        // 구독 정보 확인
        UserSubscription subscription = subscriptionRepository
                .findByUserAndStatus(user, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElseGet(() -> createDefaultFreeSubscription(user));

        // 일일 사용량 확인
        LocalDate today = LocalDate.now();
        DailyUsage dailyUsage = dailyUsageRepository
                .findByUserAndUsageDate(user, today)
                .orElseGet(() -> createNewDailyUsage(user, today));

        // 사용량 증가
        dailyUsage.setUsageCount(dailyUsage.getUsageCount() + 1);
        dailyUsage.setLastUsedAt(LocalDateTime.now());
        dailyUsageRepository.save(dailyUsage);

        int limit = subscription.getPlanType() == UserSubscription.PlanType.PREMIUM ? PREMIUM_PLAN_LIMIT
                : FREE_PLAN_LIMIT;

        log.info("User {} usage incremented: {}/{}", userNo, dailyUsage.getUsageCount(), limit);
        return true;
    }

    public int getRemainingUsage(Long userNo) {
        User user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userNo));

        UserSubscription subscription = subscriptionRepository
                .findByUserAndStatus(user, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElseGet(() -> createDefaultFreeSubscription(user));

        LocalDate today = LocalDate.now();
        DailyUsage dailyUsage = dailyUsageRepository
                .findByUserAndUsageDate(user, today)
                .orElse(null);

        int limit = subscription.getPlanType() == UserSubscription.PlanType.PREMIUM ? PREMIUM_PLAN_LIMIT
                : FREE_PLAN_LIMIT;

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
}