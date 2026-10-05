package com.trainconcierge.cab;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a cab / taxi booking for last-mile transport or disruption recovery.
 *
 * Linked to a Journey so the full disruption recovery chain can be reconstructed.
 * externalBookingRef stores the simulated confirmation number from MockCabService
 * (prefixed SIM-CAB-). All status values are suffixed _SIMULATED so there is
 * never confusion with a real provider.
 *
 * Relationships:
 * - Many CabBookings → One User
 * - Many CabBookings → One Journey
 * - One CabBooking → Many CabModificationAudit (cascade persisted when rescheduled)
 */
@Entity
@Table(name = "cab_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabBooking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_id")
    private Journey journey;

    @Column(nullable = false, length = 200)
    private String pickupAddress;

    @Column(nullable = false, length = 200)
    private String dropoffAddress;

    @Column(nullable = false)
    private LocalDateTime scheduledPickupTime;

    private LocalDateTime actualPickupTime;

    @Column(length = 50)
    @Builder.Default
    private String cabType = "STANDARD";

    @Column(precision = 8, scale = 2)
    private BigDecimal estimatedFare;

    @Column(precision = 8, scale = 2)
    private BigDecimal actualFare;

    @Column(length = 3)
    @Builder.Default
    private String currency = "GBP";

    /** Provider name e.g. Uber, Bolt, Ola, MOCK_CAB. */
    @Column(length = 50)
    @Builder.Default
    private String provider = "MOCK_CAB";

    /** Confirmation ID from the simulated ride-hailing API. */
    @Column(length = 100)
    private String externalBookingRef;

    @Column(nullable = false, length = 40, unique = true)
    private String cabBookingReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CabBookingStatus status = CabBookingStatus.CONFIRMED_SIMULATED;

    private Instant confirmedAt;

    private Instant rescheduledAt;

    @OneToMany(mappedBy = "cabBooking", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CabModificationAudit> modifications = new ArrayList<>();
}
