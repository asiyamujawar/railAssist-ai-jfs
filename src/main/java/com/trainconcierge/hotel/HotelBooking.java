package com.trainconcierge.hotel;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a hotel booking made as part of disruption recovery or journey planning.
 *
 * Linked to a Journey so we can associate hotel stays with the impacted trip.
 * externalBookingRef stores the simulated confirmation number from MockHotelService
 * (prefixed SIM-HOTEL-). All status values are suffixed _SIMULATED so there is
 * never confusion with a real provider.
 *
 * Relationships:
 * - Many HotelBookings → One User
 * - Many HotelBookings → One Journey
 * - One HotelBooking → Many HotelModificationAudit (cascade persisted when rescheduled)
 */
@Entity
@Table(name = "hotel_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelBooking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_id")
    private Journey journey;

    @Column(nullable = false, length = 150)
    private String hotelName;

    @Column(nullable = false, length = 200)
    private String hotelAddress;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    private Integer numberOfNights;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 3)
    @Builder.Default
    private String currency = "GBP";

    /** Simulated confirmation number returned by the adapter. */
    @Column(length = 100)
    private String externalBookingRef;

    @Column(nullable = false, length = 40, unique = true)
    private String hotelBookingReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private HotelBookingStatus status = HotelBookingStatus.CONFIRMED_SIMULATED;

    private Instant confirmedAt;

    private Instant rescheduledAt;

    @OneToMany(mappedBy = "hotelBooking", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<HotelModificationAudit> modifications = new ArrayList<>();
}
