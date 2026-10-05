package com.trainconcierge.schedule;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.train.Train;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents one scheduled run of a Train on a specific date.
 *
 * A Train (e.g. "12301") may run daily — each run is one TrainSchedule row.
 * Actual departure/arrival are updated in real time; scheduled times are immutable.
 *
 * Relationships:
 * - Many TrainSchedules → One Train
 * - One TrainSchedule → many Bookings
 * - One TrainSchedule → many SeatAvailability records
 * - One TrainSchedule → many DisruptionEvents
 */
@Entity
@Table(
    name = "train_schedules",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_schedule_train_date",
        columnNames = {"train_id", "scheduledDate"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private LocalTime scheduledDeparture;

    @Column(nullable = false)
    private LocalTime scheduledArrival;

    /** Updated in real time when delays are reported. */
    private LocalTime actualDeparture;

    /** Updated in real time when train arrives. */
    private LocalTime actualArrival;

    /** Delay in minutes — positive = late. */
    @Column(nullable = false)
    @Builder.Default
    private Integer delayMinutes = 0;

    /** Platform number at origin station. */
    @Column(length = 10)
    private String platform;

    @Column(nullable = false)
    @Builder.Default
    private boolean cancelled = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ScheduleStatus scheduleStatus = ScheduleStatus.SCHEDULED;

    /** Suggested base fare used as reference (e.g. INR / USD) for display.
     *  Actual per-class fares are stored on SeatAvailability. */
    @Column(precision = 10, scale = 2)
    private BigDecimal baseFare;
}
