package com.trainconcierge.seat;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Immutable record of every seat-availability alert event.
 *
 * <p>Every time the monitor detects that a subscription's threshold condition
 * is met, one row is appended here. This table is never updated or deleted
 * (append-only audit log).</p>
 *
 * <p>Relationships:
 * <ul>
 *   <li>Many SeatAlertHistory → One SeatAlertSubscription</li>
 *   <li>Many SeatAlertHistory → One User (denormalised for query convenience)</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "seat_alert_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatAlertHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SeatAlertSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Available seat count at the moment the alert fired. */
    @Column(nullable = false)
    private int availableSeatsAtAlert;

    /** The threshold that triggered this alert. */
    @Column(nullable = false)
    private int threshold;

    /** Timestamp when the monitor detected the threshold condition. */
    @Column(nullable = false)
    private Instant alertedAt;

    /** Human-readable summary for in-app display. */
    @Column(nullable = false, length = 500)
    private String message;
}
