package com.trainconcierge.admin.simulation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body for the admin "trigger delay" simulation endpoint.
 *
 * <p><strong>SIMULATED SERVICE</strong> — no real railway provider is contacted.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerDelayRequest {

    /** ID of the TrainSchedule to delay. */
    @NotNull(message = "scheduleId is required")
    private Long scheduleId;

    /** Minutes of delay — must be at least 1. */
    @NotNull(message = "delayMinutes is required")
    @Min(value = 1, message = "delayMinutes must be >= 1")
    private Integer delayMinutes;

    /** Optional human-readable operator note. */
    private String reason;
}
