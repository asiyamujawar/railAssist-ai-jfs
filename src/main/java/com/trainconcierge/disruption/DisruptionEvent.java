package com.trainconcierge.disruption;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.schedule.TrainSchedule;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Captures a disruption event affecting a TrainSchedule and/or specific Journey.
 *
 * Relationships:
 * - Many DisruptionEvents → One TrainSchedule
 * - Many DisruptionEvents → One Journey (optional / specific affected journey)
 * - One DisruptionEvent → many Recommendations
 * - One DisruptionEvent → many RebookingHistory records
 */
@Entity
@Table(name = "disruption_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisruptionEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private TrainSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_id")
    private Journey journey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisruptionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisruptionSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DisruptionStatus status = DisruptionStatus.DETECTED;

    @Column(nullable = false)
    private Instant detectedAt;

    /** When the disruption was resolved — null if still active. */
    private Instant resolvedAt;

    /** Short description visible to passengers. */
    @Column(nullable = false, length = 500)
    private String description;

    /** Internal operator notes. */
    @Column(length = 1000)
    private String operatorNotes;

    /** Estimated additional delay caused by this disruption (minutes). */
    @Column(nullable = false)
    @Builder.Default
    private Integer estimatedDelayMinutes = 0;

    @Column(length = 50)
    private String trainNumber;

    @Column(length = 20)
    private String platform;

    @Column(nullable = false)
    @Builder.Default
    private boolean resolved = false;

    /** Whether automated rebooking was triggered for this event. */
    @Column(nullable = false)
    @Builder.Default
    private boolean rebookingTriggered = false;

    /**
     * Checks if event is resolved either by boolean flag or status enum.
     */
    public boolean isResolved() {
        return resolved || status == DisruptionStatus.RESOLVED;
    }
}
