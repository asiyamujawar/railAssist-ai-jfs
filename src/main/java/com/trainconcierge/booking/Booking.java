package com.trainconcierge.booking;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.seat.SeatClass;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a single ticket booking for a TrainSchedule by a User.
 *
 * A Journey may contain multiple Bookings (multi-leg trips).
 * When a booking is rebooked after a disruption, its status becomes REBOOKED
 * and a new Booking is created — preserving full history.
 *
 * bookingReference is a human-readable unique code (e.g. "TC-20261001-0001").
 *
 * Relationships:
 * - Many Bookings → One User
 * - Many Bookings → One TrainSchedule
 * - Many Bookings → One Journey (optional — single bookings may not have a journey)
 * - One Booking → many RebookingHistory records (as original booking)
 */
@Entity
@Table(
    name = "bookings",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_booking_reference",
        columnNames = "bookingReference"
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private TrainSchedule schedule;

    /** Journey this booking belongs to — nullable for standalone bookings. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_id")
    private Journey journey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatClass seatClass;

    @Column(nullable = false)
    private Integer numberOfSeats;

    /** Total fare paid including all fees. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFare;

    /** Base fare before taxes and fees. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    /** ISO 4217 currency code e.g. GBP, INR, EUR. */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "GBP";

    /** Seat numbers as a comma-separated string e.g. "12A,12B". */
    @Column(length = 255)
    private String seatNumbers;

    private Instant confirmedAt;
    private Instant cancelledAt;

    /** If this booking was rebooked, points to the replacement booking. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_booking_id")
    private Booking replacementBooking;
}
