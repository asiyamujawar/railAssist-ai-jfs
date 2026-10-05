package com.trainconcierge.journey;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Groups one or more Bookings into a logical end-to-end trip.
 *
 * Example: London → Edinburgh may involve two bookings (two legs).
 * The Journey captures the overall origin, destination, and status.
 *
 * Relationships:
 * - Many Journeys → One User
 * - One Journey → many Bookings (mapped on Booking side)
 * - One Journey → many DisruptionEvents (via affected schedule)
 * - One Journey → many RebookingHistory records
 */
@Entity
@Table(name = "journeys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Journey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String originStation;

    @Column(nullable = false, length = 100)
    private String destinationStation;

    @Column(nullable = false)
    private LocalDate travelDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JourneyStatus status = JourneyStatus.PLANNED;

    /** Sum of all booking fares in this journey. */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 3)
    @Builder.Default
    private String currency = "GBP";

    /** Free text notes — e.g. "requires assistance", "travelling with pet". */
    @Column(length = 500)
    private String notes;
}
