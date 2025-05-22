package com.example.auth_service.schedule_service.domain;

import lombok.*;
import jakarta.persistence.*;

@Entity
@Table(name = "saved_schedule_segments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedScheduleSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_schedule_id", nullable = false)
    private SavedSchedule savedSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_item_id", nullable = false)
    private SavedScheduleItem fromItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_item_id", nullable = false)
    private SavedScheduleItem toItem;

    private Double distance;

    private Integer duration;

    @Column(name = "transport_mode")
    private String transportMode; // WALK, BUS, SUBWAY, TAXI
}