package com.trainconcierge.admin.simulation.dto;

import lombok.*;

import java.time.Instant;

/**
 * Response returned after triggering a manual monitoring cycle.
 *
 * <p>Exposes the cycle statistics gathered during the run so administrators can
 * confirm that the trigger had the expected effect.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringCycleTriggerResponse {

    /** Timestamp when the cycle started. */
    private Instant cycleStartedAt;

    /** Timestamp when the cycle completed. */
    private Instant cycleCompletedAt;

    /** Number of active journeys evaluated in this cycle. */
    private int journeysEvaluated;

    /** Number of distinct schedules polled. */
    private int schedulesPolled;

    /** Number of status changes detected and delegated to disruption detection. */
    private int changesDetected;

    /** Human-readable outcome summary. */
    private String summary;
}
