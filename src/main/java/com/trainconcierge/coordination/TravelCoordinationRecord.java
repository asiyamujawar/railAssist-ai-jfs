package com.trainconcierge.coordination;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.rebooking.RebookingHistory;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Permanent audit record of a Travel Coordination Workflow execution following a train rebooking.
 *
 * <p>Tracks original vs revised dates/times for hotel and cab reservations, explicitly recording
 * success, partial failure, or no-action status to guarantee transparency.</p>
 */
@Entity
@Table(name = "travel_coordination_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelCoordinationRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rebooking_history_id", nullable = false)
    private RebookingHistory rebookingHistory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false)
    private Journey journey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String trainRebookingStatus = "COMPLETED";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CoordinationItemStatus hotelUpdateStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CoordinationItemStatus cabUpdateStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CoordinationOverallStatus overallStatus;

    // ── Hotel Reschedule Details ──────────────────────────────
    private LocalDate hotelOldCheckIn;
    private LocalDate hotelNewCheckIn;
    private LocalDate hotelOldCheckOut;
    private LocalDate hotelNewCheckOut;

    // ── Cab Reschedule Details ────────────────────────────────
    private LocalDateTime cabOldPickupTime;
    private LocalDateTime cabNewPickupTime;

    // ── Failure & Audit Notes ─────────────────────────────────
    @Column(length = 1000)
    private String failureDetails;

    @Column(nullable = false, length = 500)
    @Builder.Default
    private String simulatedNotice = "All hotel and cab reschedules were simulated locally via Mock services. No real external provider was contacted.";

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    private Instant initiatedAt;
    private Instant completedAt;
}
