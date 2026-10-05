package com.trainconcierge.seat;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents a user's subscription to be alerted when a seat becomes
 * available on a specific TrainSchedule in a specific SeatClass.
 *
 * When SeatAvailability.availableSeats > 0 for the watched (schedule, seatClass),
 * the alert engine fires a Notification and marks this subscription as triggered.
 *
 * A user may subscribe to the same schedule multiple times only if prior
 * subscriptions have been triggered or cancelled (enforced via the active flag).
 *
 * Relationships:
 * - Many SeatAlertSubscriptions → One User
 * - Many SeatAlertSubscriptions → One TrainSchedule
 */
@Entity
@Table(
    name = "seat_alert_subscriptions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_alert_user_schedule_class",
        columnNames = {"user_id", "schedule_id", "seatClass"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatAlertSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private TrainSchedule schedule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatClass seatClass;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Alert fires when availableSeats <= threshold.
     * Defaults to 0 (fire only when a seat opens from zero).
     * A user can set threshold=5 to be alerted when 5 or fewer seats remain.
     */
    @Column(nullable = false)
    @Builder.Default
    private int threshold = 0;

    /** Set to true once the alert condition has been detected and a notification created. */
    @Column(nullable = false)
    @Builder.Default
    private boolean triggered = false;

    /** Timestamp of the first alert trigger. */
    private Instant triggeredAt;

    /**
     * Timestamp of the most recent alert notification for this subscription.
     * Used to prevent duplicate alerts when the condition has not changed.
     */
    private Instant lastAlertedAt;

    /** Total number of alert notifications generated for this subscription. */
    @Column(nullable = false)
    @Builder.Default
    private int alertCount = 0;
}
