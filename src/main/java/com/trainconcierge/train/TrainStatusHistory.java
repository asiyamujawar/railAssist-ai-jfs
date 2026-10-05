package com.trainconcierge.train;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.schedule.TrainSchedule;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Immutable audit log of every status change for a TrainSchedule.
 *
 * Each row records one transition: what the status changed to,
 * when it changed, the recorded delay, and any message from the operator.
 * Rows are never updated or deleted — append-only history.
 *
 * Relationships:
 * - Many TrainStatusHistory → One TrainSchedule
 */
@Entity
@Table(name = "train_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private TrainSchedule schedule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainStatus status;

    @Column(nullable = false)
    private Instant recordedAt;

    /** Delay in minutes at the time this status was recorded. */
    @Column(nullable = false)
    @Builder.Default
    private Integer delayMinutes = 0;

    /** Human-readable message from the operator e.g. "Awaiting crew". */
    @Column(length = 500)
    private String message;

    /** Station where this status was recorded, if applicable. */
    @Column(length = 100)
    private String recordedAtStation;

    /** Platform at origin station — populated when status = PLATFORM_CHANGED. */
    @Column(length = 10)
    private String platform;

    /** Whether this entry was recorded by the simulation engine. */
    @Column(nullable = false)
    @Builder.Default
    private boolean simulated = true;
}
