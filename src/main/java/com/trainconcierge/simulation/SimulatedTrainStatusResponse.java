package com.trainconcierge.simulation;

import com.trainconcierge.train.TrainStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulatedTrainStatusResponse {

    // ── [SIMULATED] label ───────────────────────────────────────────────────
    private final String source = "MOCK_TRAIN_STATUS_SERVICE";
    private final String disclaimer = "This status is simulated. No real railway data provider has been contacted.";

    // ── Schedule identity ───────────────────────────────────────────────────
    private Long scheduleId;
    private String trainNumber;
    private String trainName;
    private LocalDate scheduledDate;
    private LocalTime scheduledDeparture;
    private LocalTime scheduledArrival;

    // ── Current simulated status ────────────────────────────────────────────
    private TrainStatus status;
    private Integer delayMinutes;
    private String platform;
    private String message;
    private Instant lastUpdatedAt;

    // ── Whether any history exists ──────────────────────────────────────────
    private boolean hasHistory;
}
