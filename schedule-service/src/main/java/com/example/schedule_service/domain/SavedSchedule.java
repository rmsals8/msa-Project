package com.example.schedule_service.domain;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    
    @Column(name = "is_deleted", nullable = false)
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
    
    @OneToMany(mappedBy = "savedSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SavedScheduleSegment> segments = new ArrayList<>();
}