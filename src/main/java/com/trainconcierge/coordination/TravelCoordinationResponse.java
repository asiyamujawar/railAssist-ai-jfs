package com.trainconcierge.coordination;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO describing the outcome of a travel coordination workflow execution.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelCoordinationResponse {

    private Long coordinationRecordId;
    private Long rebookingHistoryId;
    private Long journeyId;
    private Long userId;

    private String trainRebookingStatus;
    private CoordinationItemStatus hotelUpdateStatus;
    private CoordinationItemStatus cabUpdateStatus;
    private CoordinationOverallStatus overallStatus;

    // ── Hotel Reschedule Summary ──────────────────────────────
    private LocalDate hotelOldCheckIn;
    private LocalDate hotelNewCheckIn;
    private LocalDate hotelOldCheckOut;
    private LocalDate hotelNewCheckOut;

    // ── Cab Reschedule Summary ────────────────────────────────
    private LocalDateTime cabOldPickupTime;
    private LocalDateTime cabNewPickupTime;

    // ── Notice & Failures ─────────────────────────────────────
    private String failureDetails;
    private String simulatedNotice;
    private Integer retryCount;

    private Instant initiatedAt;
    private Instant completedAt;
}
