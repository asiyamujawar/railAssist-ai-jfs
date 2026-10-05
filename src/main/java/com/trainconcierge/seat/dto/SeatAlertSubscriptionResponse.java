package com.trainconcierge.seat.dto;

import com.trainconcierge.seat.SeatClass;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatAlertSubscriptionResponse {

    private Long id;

    // ── Schedule info ───────────────────────────────────────────────────────
    private Long scheduleId;
    private String trainNumber;
    private String trainName;
    private LocalDate scheduledDate;

    // ── Alert config ────────────────────────────────────────────────────────
    private SeatClass seatClass;
    private int threshold;
    private boolean active;

    // ── Alert state ─────────────────────────────────────────────────────────
    private boolean triggered;
    private int alertCount;
    private Instant triggeredAt;
    private Instant lastAlertedAt;

    // ── Current seat count snapshot ─────────────────────────────────────────
    /** Available seats at the time of this response — live DB value. */
    private Integer currentAvailableSeats;

    private Instant createdAt;
    private Instant updatedAt;
}
