// 1. SavedSchedule 엔티티 수정 - 컬렉션 중 하나를 Set으로 변경
package com.example.auth_service.schedule_service.domain;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet; // Set 사용을 위해 import
import java.util.ArrayList;
import java.util.List;
import java.util.Set; // Set 사용을 위해 import

@Entity
@Table(name = "saved_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_no", nullable = false)
    private Long userNo;

    @Column(name = "schedule_name", nullable = false)
    private String scheduleName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expiration_date", nullable = false)
    private LocalDateTime expirationDate;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1)")
    @Builder.Default
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "total_distance")
    private Double totalDistance;

    @Column(name = "total_time")
    private Integer totalTime;

    @Column(name = "total_cost")
    private Double totalCost;

    @OneToMany(mappedBy = "savedSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SavedScheduleItem> scheduleItems = new ArrayList<>();

    // 컬렉션 타입을 List에서 Set으로 변경
    @OneToMany(mappedBy = "savedSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<SavedScheduleSegment> segments = new HashSet<>();
}
