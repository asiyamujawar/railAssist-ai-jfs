package com.trainconcierge.rebooking;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.disruption.DisruptionEvent;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.recommendation.Recommendation;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Permanent audit record of every rebooking attempt — successful or not.
 *
 * Design intent: the original Booking is NEVER deleted or mutated beyond
 * its status field. This table records what happened, linking the old booking
 * to the new one, the disruption that caused it, and the recommendation
 * (if any) that was accepted.
 *
 * This supports:
 * - Full disruption recovery audit trail
 * - Passenger dispute resolution
 * - AI model training data for future recommendations
 *
 * Relationships:
 * - Many RebookingHistory → One User
 * - Many RebookingHistory → One DisruptionEvent
 * - Many RebookingHistory → One Journey
 * - Many RebookingHistory → One Booking (original)
 * - Many RebookingHistory → One Booking (new) — nullable until completed
 * - Many RebookingHistory → One Recommendation — nullable for manual rebookings
 */
@Entity
@Table(name = "rebooking_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RebookingHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disruption_event_id", nullable = false)
    private DisruptionEvent disruptionEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_id")
    private Journey journey;

    /** The booking that was disrupted. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_booking_id", nullable = false)
    private Booking originalBooking;

    /** The new booking created after rebooking — null until COMPLETED. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_booking_id")
    private Booking newBooking;

    /** The recommendation the passenger accepted — null for manual rebookings. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id")
    private Recommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private RebookingStatus status = RebookingStatus.PENDING;

    /** Whether the system initiated this rebooking (true) or the passenger did (false). */
    @Column(nullable = false)
    @Builder.Default
    private boolean autonomous = true;

    /** Fare difference: newFare - originalFare. Negative = refund due. */
    @Column(precision = 10, scale = 2)
    private BigDecimal fareDifference;

    private Instant initiatedAt;
    private Instant completedAt;

    /** Reason recorded if rebooking was declined or failed. */
    @Column(length = 500)
    private String failureReason;
}
