package com.trainconcierge.seat;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.schedule.TrainSchedule;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Tracks seat availability per class for a specific TrainSchedule.
 *
 * One row per (schedule, seatClass) — updated as bookings are made/cancelled.
 * This is the source of truth for real-time availability checks and seat alerts.
 *
 * Relationships:
 * - Many SeatAvailability → One TrainSchedule
 * - One SeatAvailability → many SeatAlertSubscriptions
 */
@Entity
@Table(
    name = "seat_availability",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_seat_schedule_class",
        columnNames = {"schedule_id", "seatClass"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatAvailability extends BaseEntity {

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private TrainSchedule schedule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatClass seatClass;

    @Column(nullable = false)
    private Integer totalSeats;

    @Column(nullable = false)
    private Integer availableSeats;

    @Column(nullable = false)
    private Integer bookedSeats;

    /** Per-passenger fare in local currency for this seat-class on this schedule-run. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fare = BigDecimal.ZERO;
}
