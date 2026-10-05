package com.trainconcierge.rebooking.dto;

import com.trainconcierge.rebooking.RebookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Response DTO returned after a successful (or failed) rebooking attempt.
 * Contains both original and new booking details for the passenger.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RebookingResponse {

    private Long rebookingHistoryId;
    private Long disruptionEventId;
    private Long journeyId;

    // ── Original booking ──────────────────────────────────────
    private Long originalBookingId;
    private String originalBookingReference;
    private String originalTrainNumber;
    private String originalTrainName;
    private LocalDate originalJourneyDate;
    private LocalTime originalDeparture;
    private LocalTime originalArrival;
    private BigDecimal originalFare;
    private String originalBookingStatus;

    // ── New booking ──────────────────────────────────────────
    private Long newBookingId;
    private String newBookingReference;
    private String newTrainNumber;
    private String newTrainName;
    private LocalDate newJourneyDate;
    private LocalTime newDeparture;
    private LocalTime newArrival;
    private BigDecimal newFare;
    private String newBookingStatus;
    private Integer numberOfSeats;
    private String seatClass;
    private String currency;

    // ── Summary ──────────────────────────────────────────────
    private BigDecimal fareDifference;
    private RebookingStatus rebookingStatus;
    private boolean autonomous;
    private Instant initiatedAt;
    private Instant completedAt;
}
