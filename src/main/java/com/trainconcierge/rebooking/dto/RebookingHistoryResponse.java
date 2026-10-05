package com.trainconcierge.rebooking.dto;

import com.trainconcierge.rebooking.RebookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for rebooking history entries returned by
 * {@code GET /api/journeys/{id}/rebooking-history}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RebookingHistoryResponse {

    private Long id;
    private Long disruptionEventId;
    private Long journeyId;
    private Long originalBookingId;
    private String originalBookingReference;
    private Long newBookingId;
    private String newBookingReference;
    private Long recommendationId;
    private RebookingStatus status;
    private boolean autonomous;
    private BigDecimal fareDifference;
    private String failureReason;
    private Instant initiatedAt;
    private Instant completedAt;
    private Instant createdAt;
}
