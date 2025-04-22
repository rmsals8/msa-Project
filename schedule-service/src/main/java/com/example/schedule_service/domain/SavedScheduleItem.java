package com.example.schedule_service.domain;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_schedule_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedScheduleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_schedule_id", nullable = false)
    private SavedSchedule savedSchedule;
    
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;
    
    @Column(nullable = false)
    private String name;
    
    private String location;
    
    private Double latitude;
    
    private Double longitude;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(nullable = false)
    private String type; // FIXED or FLEXIBLE
    
    private Integer priority;
    
    private Integer duration;
}